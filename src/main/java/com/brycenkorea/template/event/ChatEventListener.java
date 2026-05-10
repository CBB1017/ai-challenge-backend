package com.brycenkorea.template.event;

import com.brycenkorea.template.contants.ActionStatus;
import com.brycenkorea.template.dto.ChatFirstInteractedEvent;
import com.brycenkorea.template.dto.EmailSummaryEvent;
import com.brycenkorea.template.repository.ActionRepository;
import com.brycenkorea.template.repository.ChatMessageRepository;
import com.brycenkorea.template.repository.ChatRoomRepository;
import com.brycenkorea.template.service.ActionService;
import com.brycenkorea.template.service.SseBroadcaster;
import com.brycenkorea.template.util.ChatPromptUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChatEventListener {

    private final ChatClient baseChatClient;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ActionService actionService;
    private final SseBroadcaster sseBroadcaster;

    @EventListener
    public Mono<Void> handleEmailSummary(EmailSummaryEvent event) {
        long startTime = System.currentTimeMillis();
        log.info("[{}] 이메일 비동기 요약 이벤트 수신 - 작업 시작", event.roomId());

        // 1. 메인 프로세스 정의
        Mono<Void> emailProcess = Mono.fromCallable(() -> {
                log.info("비동기 AI 도구 호출 시작 (Intent: {})", event.sop().intentId());

                // 시스템 프롬프트 구성 (SOP 룰 주입)
                String systemPrompt = ChatPromptUtil.buildSystemPrompt(event.sop().rules(), event.language());

                // AI 호출 (도구 포함)
                return baseChatClient.prompt()
                    .system(s -> s
                        .param("currentDateTime", ChatPromptUtil.getCurrentDateTime())
                        .param("currentDayOfWeek", ChatPromptUtil.getCurrentDayOfWeek(event.language()))
                        .param("context", systemPrompt))
                    .user(event.prompt())
                    .toolContext(java.util.Map.of(
                        "userId", event.userId(),
                        "userDept", event.auth().getDepartment(),
                        "userName", event.auth().getName(),
                        "roomId", event.roomId().toString()
                    ))
                    .call()
                    .content();
            })
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(result -> {
                log.info("비동기 AI 호출 완료. 결과 길이: {}", result.length());

                // 1. 기존 결과 메시지 업데이트 (Rewrite 방식)
                return chatMessageRepository.findById(event.messageId())
                    .flatMap(msg -> {
                        msg.setContent(result);
                        return chatMessageRepository.save(msg);
                    })
                    .publishOn(Schedulers.boundedElastic())
                    .doOnSuccess(savedMsg -> {
                        // SSE로 알림 전송
                        sseBroadcaster.sendEmailSummaryComplete(event.userId(), savedMsg);

                        // 비동기 요약 완료 후에도 제목 업데이트 트리거
                        chatRoomRepository.findById(event.roomId())
                            .flatMap(room -> {
                                if (isNewChatTitle(room.getTitle())) {
                                    log.info("[Title] 비동기 작업 완료 후 제목 업데이트 트리거");
                                    return handleFirstInteraction(new ChatFirstInteractedEvent(
                                        event.roomId(), event.userId(), event.prompt(), result, event.language()
                                    ));
                                }
                                return Mono.empty();
                            })
                            .subscribe();
                    })
                    .then(Mono.defer(() -> {
                        // 2. Action 상태 업데이트 (IN_PROGRESS -> SUCCESS) 및 이벤트 발행
                        return actionService.updateStatusAndBroadcast(event.roomId(), event.userId(), event.sop()
                                .intentId(), ActionStatus.SUCCESS)
                            .then();
                    }))
                    .then();
            });

        return emailProcess
            .doOnSuccess(v -> log.info("이메일 비동기 요약 프로세스 최종 완료 (총 소요시간: {}ms)", System.currentTimeMillis() - startTime))
            .doOnError(e -> {
                log.error("이메일 비동기 요약 처리 중 에러", e);
                // 에러 발생 시에도 액션 상태 업데이트 및 이벤트 발행
                actionService.updateStatusAndBroadcast(event.roomId(), event.userId(), event.sop()
                        .intentId(), ActionStatus.ERROR)
                    .subscribe();
                sseBroadcaster.sendError(event.userId(), "이메일 요약 중 오류가 발생했습니다.");
            });
    }

    private boolean isNewChatTitle(String title) {
        if (title == null) return true;
        String trimmed = title.trim();
        return java.util.List.of("새로운 대화", "New Conversation", "新しい対話", "Cuộc trò chuyện mới").contains(trimmed);
    }

    @EventListener
    public Mono<Void> handleFirstInteraction(ChatFirstInteractedEvent event) {
        log.info("[{}] 첫 대화 제목 요약 이벤트 수신", event.roomId());

        return Mono.fromCallable(() -> {
                log.info("AI 요약 요청 시작...");
                String summaryPrompt = ChatPromptUtil.getTitleSummaryPrompt(event.language(), event.userPrompt(), event.aiResponse());
                return baseChatClient.prompt().user(summaryPrompt).call().content();
            })
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(title -> {
                if (!title.isBlank()) {
                    String cleanTitle = title.replace("\"", "").trim();
                    log.info("요약 완료 -> DB 업데이트: {}", cleanTitle);
                    return chatRoomRepository.updateTitle(event.roomId(), cleanTitle)
                        .doOnSuccess(v -> {
                            sseBroadcaster.sendTitleUpdate(event.userId(),
                                java.util.Map.of("roomId", event.roomId(), "title", cleanTitle));
                        });
                }
                return Mono.empty();
            })
            .doOnSuccess(v -> log.debug("첫 대화 제목 요약 프로세스 완료"))
            .doOnError(e -> log.error("제목 생성/업데이트 중 에러", e))
            .then();
    }
}
