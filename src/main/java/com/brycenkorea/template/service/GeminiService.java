package com.brycenkorea.template.service;

import com.brycenkorea.template.config.IntentRouter;
import com.brycenkorea.template.contants.AgentWorkflowSOP;
import com.brycenkorea.template.dto.ChatFirstInteractedEvent;
import com.brycenkorea.template.dto.response.PromptResponse;
import com.brycenkorea.template.entity.ChatMessage;
import com.brycenkorea.template.entity.ChatRoom;
import com.brycenkorea.template.repository.ChatMessageRepository;
import com.brycenkorea.template.repository.ChatRoomRepository;
import com.brycenkorea.template.security.GroupwareAuthenticationToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private final ChatClient baseChatClient;
    private final RetrievalAugmentationAdvisor ragAdvisor;
    private final IntentRouter intentRouter;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ApplicationEventPublisher eventPublisher;

    public Flux<ChatResponse> askStream(String prompt, String roomId) {
        return ReactiveSecurityContextHolder.getContext()
            .mapNotNull(SecurityContext::getAuthentication)
            .cast(GroupwareAuthenticationToken.class)
            .flatMapMany(auth -> {
                AgentWorkflowSOP sop = intentRouter.classify(prompt);
                ChatClient.ChatClientRequestSpec spec = buildRequestSpec(auth, sop, prompt, roomId);

                return executeChatFlow(spec, prompt, roomId);
            });
    }
    /**
     * 방 ID가 있으면 존재 확인, 없으면 새로 생성하여 반환
     */
    public Mono<UUID> getOrCreateRoom(String roomId, String userId) {
        if (roomId == null || roomId.isBlank()) {
            return chatRoomRepository.save(ChatRoom.builder().userId(userId).title("새로운 대화").build())
                .map(ChatRoom::getRoomId);
        }
        // ID가 있으면 UUID로 변환하여 반환
        return Mono.just(UUID.fromString(roomId));
    }

    private ChatClient.ChatClientRequestSpec buildRequestSpec(GroupwareAuthenticationToken auth, AgentWorkflowSOP sop, String prompt, String roomId) {
        ChatClient.ChatClientRequestSpec spec = baseChatClient.prompt()
            .system(s -> s.param("userId", Objects.requireNonNull(auth.getPrincipal()))
                .param("userDept", auth.getDepartment())
                .param("userName", auth.getName())
                .param("context", sop.rules()))
            .user(prompt)
            .advisors(a -> a
                .param(ChatMemory.CONVERSATION_ID, roomId)
                .param("user", Map.of(
                    "id", Objects.requireNonNull(auth.getPrincipal()),
                    "dept", auth.getDepartment(),
                    "name", auth.getName()
                ))
            );

        return sop.requiresRag() ? spec.advisors(ragAdvisor) : spec;
    }

    private Flux<ChatResponse> executeChatFlow(ChatClient.ChatClientRequestSpec spec, String prompt, String roomId) {
        StringBuilder buffer = new StringBuilder();
        UUID roomUuid = UUID.fromString(roomId);

        // 1. 유저 메시지 저장
        Mono<ChatMessage> saveUserMsg = saveChatMessage(roomUuid, "USER", prompt);

        // 2. AI 스트림 정의
        Flux<ChatResponse> aiStream = Flux.defer(() -> spec.stream().chatResponse())
            .subscribeOn(Schedulers.boundedElastic())
            .doOnNext(res -> Optional.ofNullable(res.getResult())
                .map(r -> r.getOutput().getText())
                .ifPresent(buffer::append));

        // 3. 조립
        return saveUserMsg.thenMany(aiStream)
            .concatWith(saveAssistantMessageAndTitle(roomUuid, prompt, buffer));
    }

    /**
     * 채팅 메시지를 DB에 저장하는 공통 로직
     */
    private Mono<ChatMessage> saveChatMessage(UUID roomUuid, String role, String content) {
        // 빈 메시지는 저장하지 않도록 방어 로직 추가 가능
        if (content == null || content.isBlank()) {
            return Mono.empty();
        }

        return chatMessageRepository.save(
            ChatMessage.builder()
                .roomId(roomUuid)
                .role(role)
                .content(content)
                .build()
        ).doOnError(e -> log.error("{} 메시지 저장 중 에러 발생: {}", role, e.getMessage()));
    }

    private Mono<ChatResponse> saveAssistantMessageAndTitle(UUID roomUuid, String prompt, StringBuilder buffer) {
        return Mono.defer(() -> {
            String fullContent = buffer.toString();
            log.info("[Chain] 답변 저장 시작...");
            // 1. 메시지 저장 후 제목 체크/이벤트 발행
            return saveChatMessage(roomUuid, "ASSISTANT", fullContent)
                .flatMap(savedMsg -> {
                    log.info("[Chain] 답변 저장 완료, 제목 체크 시작");
                    return checkAndTriggerTitle(roomUuid, prompt, fullContent);
                })
                .then(Mono.empty());
        });
    }

    public Flux<PromptResponse> askStreamProcessed(String prompt, String roomId) {
        return askStream(prompt, roomId)
            .filter(this::isNotToolCall) // Tool Call 체크 로직 분리
            .map(this::convertToPromptResponse)
            .filter(resp -> !resp.response().isEmpty())
            .doOnNext(msg -> log.info("최종 발송 데이터: {}", msg.response()));
    }

    private boolean isNotToolCall(ChatResponse chatResponse) {
        return chatResponse.getResults().stream()
            .noneMatch(gen -> gen.getOutput().hasToolCalls());
    }

    private PromptResponse convertToPromptResponse(ChatResponse chatResponse) {
        String content = (chatResponse.getResult() != null)
            ? chatResponse.getResult().getOutput().getText()
            : "";
        return new PromptResponse(content);
    }

    private Mono<Void> checkAndTriggerTitle(UUID roomUuid, String userMsg, String aiMsg) {
        return chatRoomRepository.findById(roomUuid)
            .flatMap(room -> {
                // 공백이나 대소문자 문제일 수 있으므로 trim()과 equals 처리 주의
                if (room.getTitle() != null && room.getTitle().trim().equals("새로운 대화")) {
                    eventPublisher.publishEvent(new ChatFirstInteractedEvent(roomUuid, userMsg, aiMsg));
                }
                return Mono.empty();
            })
            .doOnError(e -> log.error("[Step 2 Error] DB 조회 중 에러: {}", e.getMessage()))
            .then();
    }
}