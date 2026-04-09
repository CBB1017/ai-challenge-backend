package com.brycenkorea.template.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatStateManager {

    private final ReactiveStringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "chat:state:";

    // 현재 상태 조회
    public Mono<String> getState(String roomId) {
        return redisTemplate.opsForValue().get(KEY_PREFIX + roomId)
            // Redis가 1초 안에 대답 안 하면 버림
            .timeout(Duration.ofSeconds(1))
            .onErrorResume(e -> {
                // Redis가 죽었거나 느리면 그냥 "상태 없음(empty)"으로 간주하고 대화 진행
                log.error("[Redis 에러] 상태 조회 실패, 무시하고 진행: {}", e.getMessage());
                return Mono.empty();
            });
    }

    // 상태 저장
    public Mono<Boolean> setState(String roomId, String state) {
        log.info("[Redis] {} 방 상태 저장: {}", roomId, state);
        return redisTemplate.opsForValue()
            .set(KEY_PREFIX + roomId, state, Duration.ofMinutes(15))
            .timeout(Duration.ofSeconds(1)) // 여기도 1초 타임아웃
            .onErrorReturn(false); // 에러 나면 그냥 false 반환하고 앱은 정상 구동
    }

    // 상태 초기화 (상신 완료 또는 취소 시)
    public Mono<Boolean> clearState(String roomId) {
        log.info("[Redis] {} 방 상태 초기화", roomId);
        return redisTemplate.delete(KEY_PREFIX + roomId)
            .map(count -> count > 0);
    }
}