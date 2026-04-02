package com.brycenkorea.template.service;

import com.brycenkorea.template.config.IntentRouter;
import com.brycenkorea.template.contants.AgentWorkflowSOP;
import com.brycenkorea.template.dto.response.PromptResponse;
import com.brycenkorea.template.entity.ChatMessage;
import com.brycenkorea.template.repository.ChatMessageRepository;
import com.brycenkorea.template.repository.ChatRoomRepository;
import com.brycenkorea.template.security.GroupwareAuthenticationToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
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

        // 1. 유저 메시지 저장 (깔끔!)
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
            return saveChatMessage(roomUuid, "ASSISTANT", fullContent)
                .doOnSuccess(saved -> triggerTitleGeneration(roomUuid.toString(), prompt, fullContent))
                .then(Mono.empty());
        });
    }

    // 💡 제목 요약 전용 비동기 메서드 (동일 클래스 내 추가)
    private Mono<?> generateAndSaveRoomTitle(String roomId, String userMsg, String aiMsg) {
        String summaryPrompt = String.format("다음 대화를 바탕으로 채팅방의 제목을 15자 이내로 요약해줘.\n유저: %s\nAI: %s", userMsg, aiMsg);

        // 💡 Callable 내부의 블로킹 코드를 명시적으로 boundedElastic 스레드에서 실행
        return Mono.fromCallable(() -> baseChatClient.prompt().user(summaryPrompt).call().content())
            .subscribeOn(Schedulers.boundedElastic()) // 👈 이 부분 중요
            .flatMap(title -> chatRoomRepository.updateTitle(UUID.fromString(roomId), title))
            .onErrorResume(e -> {
                log.error("방 제목 생성 실패", e);
                return Mono.empty();
            });
    }

    /**
     * 채팅 응답 완료 후 비동기로 방 제목 생성 및 저장 트리거
     */
    private void triggerTitleGeneration(String roomId, String userPrompt, String aiResponse) {
        // 제목 생성 로직이 메인 응답 흐름을 방해하지 않도록 비동기 처리
        generateAndSaveRoomTitle(roomId, userPrompt, aiResponse)
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess(title -> log.info("[{}] 방 제목 생성 완료: {}", roomId, title))
            .doOnError(e -> log.error("[{}] 방 제목 생성 중 에러: {}", roomId, e.getMessage()))
            .subscribe(); // 비동기 실행 트리거
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
}