package com.brycenkorea.template.event;

import com.brycenkorea.template.dto.ChatFirstInteractedEvent;
import com.brycenkorea.template.repository.ChatRoomRepository;
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

    @EventListener
    public void handleFirstInteraction(ChatFirstInteractedEvent event) {
        log.info("[{}] 첫 대화 제목 요약 이벤트 수신", event.roomId());

        // 1. Mono.fromCallable을 사용하여 블로킹 작업(AI 호출)을 감쌉니다.
        Mono.fromCallable(() -> {
                log.info("AI 요약 요청 시작...");
                String summaryPrompt = String.format(
                    "다음 대화를 바탕으로 채팅방의 제목을 15자 이내로 요약해줘.\n유저: %s\nAI: %s",
                    event.userPrompt(), event.aiResponse()
                );
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
            .doOnError(e -> log.error("제목 생성/업데이트 중 에러", e))
            // 3. 비동기 실행을 위한 명시적 구독
            .subscribe();
    }
}