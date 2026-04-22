package com.brycenkorea.template.event;

import com.brycenkorea.template.dto.ChatFirstInteractedEvent;
import com.brycenkorea.template.dto.EmailSummaryEvent;
import com.brycenkorea.template.entity.ChatMessage;
import com.brycenkorea.template.contants.ActionStatus;
import com.brycenkorea.template.repository.ChatMessageRepository;
import com.brycenkorea.template.repository.ChatRoomRepository;
import com.brycenkorea.template.repository.ActionRepository;
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
    private final ActionRepository actionRepository;

    @EventListener
    public Mono<Void> handleEmailSummary(EmailSummaryEvent event) {
        log.info("[{}] 이메일 비동기 요약 이벤트 수신", event.roomId());

        return Mono.fromCallable(() -> {
                log.info("비동기 AI 도구 호출 시작 (Intent: {})", event.sop().intentId());
                
                // 시스템 프롬프트 구성 (SOP 룰 주입)
                String systemPrompt = event.sop().rules() + "\n\n" +
                    "Please respond in the user's language (" + (event.language() != null ? event.language() : "ko") + ").";

                // AI 호출 (도구 포함)
                return baseChatClient.prompt()
                    .system(s -> s.param("context", systemPrompt))
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
                log.info("비동기 AI 호출 완료. 결과 저장 및 상태 업데이트 시작");
                
                // 1. 결과 메시지 저장
                Mono<ChatMessage> saveMsg = chatMessageRepository.save(
                    ChatMessage.builder()
                        .roomId(event.roomId())
                        .role("ASSISTANT")
                        .content(result)
                        .build()
                );

                // 2. Action 상태 업데이트 (IN_PROGRESS -> SUCCESS)
                Mono<Void> updateAction = actionRepository.findAllByRoomIdOrderByCreatedAtDesc(event.roomId())
                    .filter(a -> "EMAIL_SUMMARY".equals(a.getActionName()) && a.getStatus() == ActionStatus.IN_PROGRESS)
                    .next()
                    .flatMap(action -> {
                        action.setStatus(ActionStatus.SUCCESS);
                        return actionRepository.save(action);
                    })
                    .then();

                return saveMsg.then(updateAction);
            })
            .onErrorResume(e -> {
                log.error("이메일 비동기 요약 처리 중 에러", e);
                // 에러 발생 시 Action 상태 업데이트
                return actionRepository.findAllByRoomIdOrderByCreatedAtDesc(event.roomId())
                    .filter(a -> "EMAIL_SUMMARY".equals(a.getActionName()) && a.getStatus() == ActionStatus.IN_PROGRESS)
                    .next()
                    .flatMap(action -> {
                        action.setStatus(ActionStatus.ERROR);
                        action.setContent("Error: " + e.getMessage());
                        return actionRepository.save(action);
                    })
                    .then();
            })
            .doOnSuccess(v -> log.info("이메일 비동기 요약 프로세스 최종 완료"))
            .doOnError(e -> log.error("최종 구독 에러 (상태 업데이트 실패 가능성)", e));
    }

    @EventListener
    public Mono<Integer> handleFirstInteraction(ChatFirstInteractedEvent event) {
        log.info("[{}] 첫 대화 제목 요약 이벤트 수신", event.roomId());

        // 1. Mono.fromCallable을 사용하여 블로킹 작업(AI 호출)을 감쌉니다.
        return Mono.fromCallable(() -> {
                log.info("AI 요약 요청 시작...");
                String summaryPrompt = switch (event.language() != null ? event.language().toLowerCase() : "ko") {
                    case "en" -> String.format(
                        "Please summarize the following conversation as a chat room title within 15 characters.\nUser: %s\nAI: %s",
                        event.userPrompt(), event.aiResponse()
                    );
                    case "ja" -> String.format(
                        "次の会話を元に、チャットルームのタイトルを15文字以内で要約してください。\nユーザー: %s\nAI: %s",
                        event.userPrompt(), event.aiResponse()
                    );
                    case "vi" -> String.format(
                        "Dựa trên cuộc trò chuyện sau, hãy tóm tắt tiêu đề phòng trò chuyện trong vòng 15 ký tự.\nNgười dùng: %s\nAI: %s",
                        event.userPrompt(), event.aiResponse()
                    );
                    default -> String.format(
                        "다음 대화를 바탕으로 채팅방의 제목을 15자 이내로 요약해줘.\n유저: %s\nAI: %s",
                        event.userPrompt(), event.aiResponse()
                    );
                };
                return baseChatClient.prompt().user(summaryPrompt).call().content();
            })
            // 2. 블로킹 AI 호출을 위한 전용 스레드 풀 할당
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(title -> {
                if (!title.isBlank()) {
                    String cleanTitle = title.replace("\"", "").trim();
                    log.info("요약 완료 -> DB 업데이트: {}", cleanTitle);
                    return chatRoomRepository.updateTitle(event.roomId(), cleanTitle);
                }
                return Mono.empty();
            })
            .doOnSuccess(v -> log.debug("첫 대화 제목 요약 프로세스 완료"))
            .doOnError(e -> log.error("제목 생성/업데이트 중 에러", e));
    }
}