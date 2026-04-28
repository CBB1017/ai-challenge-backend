package com.brycenkorea.template.event;

import com.brycenkorea.template.dto.ChatFirstInteractedEvent;
import com.brycenkorea.template.dto.EmailSummaryEvent;
import com.brycenkorea.template.dto.response.ActionResponse;
import com.brycenkorea.template.entity.ChatMessage;
import com.brycenkorea.template.contants.ActionStatus;
import com.brycenkorea.template.repository.ChatMessageRepository;
import com.brycenkorea.template.repository.ChatRoomRepository;
import com.brycenkorea.template.repository.ActionRepository;
import com.brycenkorea.template.service.SseBroadcaster;
import com.brycenkorea.template.util.ChatPromptUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChatEventListener {

    private final ChatClient baseChatClient;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ActionRepository actionRepository;
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
                        "userName", event.auth().getName()
                    ))
                    .call()
                    .content();
            })
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(result -> {
                long aiDuration = System.currentTimeMillis() - startTime;
                log.info("비동기 AI 호출 완료 (소요시간: {}ms). 결과 길이: {}", aiDuration, result.length());
                
                // 1. 기존 결과 메시지 업데이트 (Rewrite 방식)
                return chatMessageRepository.findById(event.messageId())
                    .flatMap(msg -> {
                        msg.setContent(result);
                        return chatMessageRepository.save(msg);
                    })
                    .doOnSuccess(savedMsg -> {
                        // SSE로 알림 전송
                        sseBroadcaster.sendEmailSummaryComplete(event.userId(), savedMsg);
                    })
                    .then(Mono.defer(() -> {
                        // 2. Action 상태 업데이트 (IN_PROGRESS -> SUCCESS)
                        return actionRepository.findAllByRoomIdOrderByCreatedAtDesc(event.roomId())
                            .filter(a -> event.sop().intentId().equals(a.getActionName()) && a.getStatus() == ActionStatus.IN_PROGRESS)
                            .next()
                            .flatMap(action -> {
                                action.setStatus(ActionStatus.SUCCESS);
                                return actionRepository.save(action);
                            });
                    }))
                    .then();
            });

        // 2. 액션 상태 주기적 푸시 스트림
        Flux<Void> actionPushStream = Flux.interval(Duration.ofSeconds(2))
            .flatMap(i -> actionRepository.findAllByRoomIdOrderByCreatedAtDesc(event.roomId())
                .map(ActionResponse::from)
                .collectList())
            .doOnNext(actions -> sseBroadcaster.sendEvent(event.userId(), "action-list-update", actions))
            .then().flux();

        // 3. 합치기: emailProcess와 actionPushStream을 병렬로 실행하되, 공유 구독을 통해 중복 실행 방지
        return emailProcess.flux().publish(shared ->
                Flux.merge(
                    shared,
                    actionPushStream.takeUntilOther(shared.then())
                )
            )
            .then()
            .doOnSuccess(v -> log.info("이메일 비동기 요약 프로세스 최종 완료 (총 소요시간: {}ms)", System.currentTimeMillis() - startTime))
            .doOnError(e -> {
                log.error("이메일 비동기 요약 처리 중 에러", e);
                sseBroadcaster.sendError(event.userId(), "이메일 요약 중 오류가 발생했습니다.");
            });
    }

    @EventListener
    public Mono<Void> handleFirstInteraction(ChatFirstInteractedEvent event) {
        log.info("[{}] 첫 대화 제목 요약 이벤트 수신", event.roomId());

        // 1. Mono.fromCallable을 사용하여 블로킹 작업(AI 호출)을 감쌉니다.
        return Mono.fromCallable(() -> {
                log.info("AI 요약 요청 시작...");
                String summaryPrompt = ChatPromptUtil.getTitleSummaryPrompt(event.language(), event.userPrompt(), event.aiResponse());
                return baseChatClient.prompt().user(summaryPrompt).call().content();
            })
            // 2. 블로킹 AI 호출을 위한 전용 스레드 풀 할당
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(title -> {
                if (!title.isBlank()) {
                    String cleanTitle = title.replace("\"", "").trim();
                    log.info("요약 완료 -> DB 업데이트: {}", cleanTitle);
                    return chatRoomRepository.updateTitle(event.roomId(), cleanTitle)
                        .doOnSuccess(v -> {
                            // 3. SSE로 제목 업데이트 알림 전송
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