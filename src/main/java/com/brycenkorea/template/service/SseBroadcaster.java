package com.brycenkorea.template.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 프론트엔드로 실시간 알림(SSE)을 전송하기 위한 브로드캐스터 서비스입니다.
 */
@Service
@Slf4j
public class SseBroadcaster {

    // 사용자 ID별로 Sink를 관리하여 개별적인 스트림을 유지합니다.
    private final Map<String, Sinks.Many<ServerSentEvent<Object>>> userSinks = new ConcurrentHashMap<>();

    /**
     * 특정 사용자의 SSE 스트림을 구독합니다.
     * 
     * @param userId 사용자 ID
     * @return SSE 이벤트 Flux
     */
    public Flux<ServerSentEvent<Object>> subscribe(String userId) {
        log.info("SSE 구독 요청: userId={}", userId);
        
        Sinks.Many<ServerSentEvent<Object>> sink = userSinks.computeIfAbsent(userId, key -> 
            Sinks.many().multicast().directBestEffort()
        );

        // 15초마다 하트비트(주석)를 전송하여 연결 유지
        Flux<ServerSentEvent<Object>> heartbeat = Flux.interval(Duration.ofSeconds(15))
                .map(i -> ServerSentEvent.builder().comment("keep-alive").build());

        return Flux.merge(sink.asFlux(), heartbeat)
            .doOnCancel(() -> {
                log.info("SSE 구독 취소: userId={}", userId);
                userSinks.remove(userId);
            })
            .doOnTerminate(() -> userSinks.remove(userId));
    }

    /**
     * 특정 사용자에게 이벤트를 전송합니다.
     * 
     * @param userId 사용자 ID
     * @param eventName 이벤트 이름 (프론트엔드에서 고유 식별자로 사용)
     * @param data 전송할 데이터 객체
     */
    public void sendEvent(String userId, String eventName, Object data) {
        Sinks.Many<ServerSentEvent<Object>> sink = userSinks.get(userId);
        if (sink != null) {
            ServerSentEvent<Object> event = ServerSentEvent.builder()
                    .event(eventName)
                    .data(data)
                    .build();
            
            Sinks.EmitResult result = sink.tryEmitNext(event);
            if (result.isFailure()) {
                log.warn("SSE 이벤트 전송 실패 (userId={}): {}", userId, result);
            } else {
                log.debug("SSE 이벤트 전송 성공 (userId={}, event={})", userId, eventName);
            }
        } else {
            log.debug("활성화된 SSE 구독이 없음: userId={}", userId);
        }
    }

    /**
     * 이메일 요약 비동기 작업 완료 시 프론트엔드에 알림을 전송합니다.
     * 
     * @param userId 사용자 ID
     * @param summaryData 요약된 데이터 또는 완료 메시지
     */
    public void sendEmailSummaryComplete(String userId, Object summaryData) {
        sendEvent(userId, "email-summary-complete", summaryData);
    }

    /**
     * 채팅방 제목 업데이트 시 프론트엔드에 알림을 전송합니다.
     *
     * @param userId 사용자 ID
     * @param titleData 업데이트된 제목 정보
     */
    public void sendTitleUpdate(String userId, Object titleData) {
        sendEvent(userId, "chat-title-update", titleData);
    }

    /**
     * 작업 중 에러 발생 시 프론트엔드에 알림을 전송합니다.
     * 
     * @param userId 사용자 ID
     * @param errorMessage 에러 메시지
     */
    public void sendError(String userId, String errorMessage) {
        sendEvent(userId, "error", Map.of("message", errorMessage));
    }
}
