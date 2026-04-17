package com.brycenkorea.template.service;

import com.brycenkorea.template.config.IntentRouter;
import com.brycenkorea.template.contants.ActionStatus;
import com.brycenkorea.template.contants.AgentWorkflowSOP;
import com.brycenkorea.template.dto.ChatFirstInteractedEvent;
import com.brycenkorea.template.dto.response.PromptResponse;
import com.brycenkorea.template.entity.ChatMessage;
import com.brycenkorea.template.entity.ChatRoom;
import com.brycenkorea.template.repository.ChatMessageRepository;
import com.brycenkorea.template.repository.ChatRoomRepository;
import com.brycenkorea.template.security.GroupwareAuthenticationToken;
import com.brycenkorea.template.util.LocalizationUtil;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;

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
    private final ActionService actionService;

    public Mono<AgentWorkflowSOP> determineIntent(String prompt, String roomId) {
        return intentRouter.determineSop(prompt, roomId);
    }

    public Flux<PromptResponse> askStreamWithSop(String prompt, String roomId, String language, AgentWorkflowSOP sop) {
        return ReactiveSecurityContextHolder.getContext()
            .mapNotNull(SecurityContext::getAuthentication)
            .cast(GroupwareAuthenticationToken.class)
            .flatMapMany(auth -> {
                ChatClient.ChatClientRequestSpec spec = buildRequestSpec(auth, sop, prompt, roomId, language);
                return executeChatFlow(spec, prompt, roomId, sop, auth.getPrincipal(), language);
            })
            .filter(this::isNotToolCall)
            .map(this::convertToPromptResponse)
            .filter(resp -> !resp.response().isEmpty())
            .onErrorResume(e -> {
                log.error("[Gemini 에러 감지] 원인: {}", e.getMessage());
                String userFriendlyMessage = LocalizationUtil.getErrorMessage(language, "general");

                if (e.getMessage().contains("429") || e.getMessage().contains("quota")) {
                    userFriendlyMessage = LocalizationUtil.getErrorMessage(language, "quota");
                } else if (e.getMessage().contains("safety")) {
                    userFriendlyMessage = LocalizationUtil.getErrorMessage(language, "safety");
                }

                return Flux.just(new PromptResponse(userFriendlyMessage));
            });
    }

    /**
     * 방 ID가 있으면 존재 확인, 없으면 새로 생성하여 반환
     */
    public Mono<UUID> getOrCreateRoom(String roomId, String userId, String language) {
        if (roomId == null || roomId.isBlank()) {
            String title = LocalizationUtil.getNewChatTitle(language);
            return chatRoomRepository.save(ChatRoom.builder().userId(userId).title(title).build())
                .map(ChatRoom::getRoomId);
        }
        // ID가 있으면 UUID로 변환하여 반환
        return Mono.just(UUID.fromString(roomId));
    }

    private ChatClient.ChatClientRequestSpec buildRequestSpec(GroupwareAuthenticationToken auth, AgentWorkflowSOP sop, String prompt, String roomId, String language) {
        log.info("sop rule => {}", sop.rules());
        LocalDateTime now = LocalDateTime.now();
        String currentDateTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Locale locale = LocalizationUtil.getLocale(language);
        String currentDayOfWeek = now.getDayOfWeek().getDisplayName(TextStyle.FULL, locale);

        String systemPrompt = sop.rules() + "\n\n" + 
            "Please respond in the user's language (" + (language != null ? language : "ko") + ").";

        ChatClient.ChatClientRequestSpec spec = baseChatClient.prompt()
            .system(s -> s
                .param("currentDateTime", currentDateTime)
                .param("currentDayOfWeek", currentDayOfWeek)
                .param("context", systemPrompt))
            .user(prompt)
            .toolContext(Map.of(
                "userId", Objects.requireNonNull(auth.getPrincipal()),
                "userDept", auth.getDepartment(),
                "userName", auth.getName(),
                "intentId", sop.intentId()
            ))
            .advisors(a -> a
                .param(ChatMemory.CONVERSATION_ID, roomId)
            );
        return sop.requiresRag() ? spec.advisors(ragAdvisor) : spec;
    }

    private Flux<ChatResponse> executeChatFlow(ChatClient.ChatClientRequestSpec spec, String prompt, String roomId, AgentWorkflowSOP sop, String userId, String language) {
        StringBuilder buffer = new StringBuilder();
        UUID roomUuid = UUID.fromString(roomId);
        // 도구 호출 여부 확인을 위한 플래그
        final boolean[] toolCalled = {false};

        // 1. 유저 메시지 저장
        Mono<ChatMessage> saveUserMsg = saveChatMessage(roomUuid, "USER", prompt);

        // 2. AI 스트림 정의
        Flux<ChatResponse> aiStream = Flux.defer(() -> spec.stream().chatResponse())
            .subscribeOn(Schedulers.boundedElastic())
            .doOnNext(res -> {
                // 도구 호출 여부 체크
                if (res.getResults().stream().anyMatch(gen -> gen.getOutput().hasToolCalls())) {
                    toolCalled[0] = true;
                }
                
                Optional.ofNullable(res.getResult())
                    .map(r -> r.getOutput().getText())
                    .ifPresent(buffer::append);
            });

        // 3. 조립
        return saveUserMsg.thenMany(aiStream)
            .concatWith(saveAssistantMessageAndTitle(roomUuid, prompt, buffer, language))
            .thenMany(Flux.<ChatResponse>defer(() -> {
                // 모든 작업 완료 후 액션 로그 기록 여부 판단
                if (toolCalled[0] && isActionIntent(sop.intentId())) {
                    String summary = prompt.length() > 200 ? prompt.substring(0, 197) + "..." : prompt;
                    return actionService.logAction(sop.intentId(), summary, ActionStatus.SUCCESS, userId, roomUuid)
                        .doOnNext(a -> log.info("[ActionLog] 액션 기록 완료: {}", a.getActionName()))
                        .thenMany(Flux.empty());
                }
                return Flux.empty();
            }))
            .onErrorResume(e -> {
                // 에러 발생 시, 액션 대상 SOP라면 에러 상태로 로그 기록
                if (isActionIntent(sop.intentId())) {
                    String summary = prompt.length() > 200 ? prompt.substring(0, 197) + "..." : prompt;
                    return actionService.logAction(sop.intentId(), summary, ActionStatus.ERROR, userId, roomUuid)
                        .doOnNext(a -> log.info("[ActionLog] 에러 액션 기록 완료: {}", a.getActionName()))
                        .then(Mono.error(e));
                }
                return Mono.error(e);
            });
    }

    private boolean isActionIntent(String intentId) {
        return List.of("OVERTIME_ONEDAY", "OVERTIME_MONTHLY", "VACATION").contains(intentId);
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

    private Mono<ChatResponse> saveAssistantMessageAndTitle(UUID roomUuid, String prompt, StringBuilder buffer, String language) {
        return Mono.defer(() -> {
            String fullContent = buffer.toString();
            log.info("[Chain] 답변 저장 시작...");
            // 1. 메시지 저장 후 제목 체크/이벤트 발행
            return saveChatMessage(roomUuid, "ASSISTANT", fullContent)
                .flatMap(savedMsg -> {
                    log.info("[Chain] 답변 저장 완료, 제목 체크 시작");
                    return checkAndTriggerTitle(roomUuid, prompt, fullContent, language);
                })
                .then(Mono.empty());
        });
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

    private Mono<Void> checkAndTriggerTitle(UUID roomUuid, String userMsg, String aiMsg, String language) {
        return chatRoomRepository.findById(roomUuid)
            .flatMap(room -> {
                // 공백이나 대소문자 문제일 수 있으므로 trim()과 equals 처리 주의
                String currentTitle = room.getTitle() != null ? room.getTitle().trim() : "";
                if (isNewChatTitle(currentTitle)) {
                    eventPublisher.publishEvent(new ChatFirstInteractedEvent(roomUuid, userMsg, aiMsg, language));
                }
                return Mono.empty();
            })
            .doOnError(e -> log.error("[Step 2 Error] DB 조회 중 에러: {}", e.getMessage()))
            .then();
    }

    private boolean isNewChatTitle(String title) {
        return List.of("새로운 대화", "New Conversation", "新しい対話", "Cuộc trò chuyện mới").contains(title);
    }
}
