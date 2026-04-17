package com.brycenkorea.template.util;

import com.brycenkorea.template.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatStateManager {

    private final ChatRoomRepository chatRoomRepository;

    // 현재 상태 조회
    public Mono<StateInfo> getState(String roomId) {
        return chatRoomRepository.findById(UUID.fromString(roomId))
            .map(chatRoom -> {
                if (chatRoom.getState() == null) {
                    return new StateInfo("EMPTY", null);
                }
                return new StateInfo(chatRoom.getState(), chatRoom.getStateUpdatedAt());
            })
            .defaultIfEmpty(new StateInfo("EMPTY", null))
            .timeout(Duration.ofSeconds(2))
            .onErrorResume(e -> {
                log.error("[DB 에러] 상태 조회 실패: {}", e.getMessage());
                return Mono.just(new StateInfo("DB_ERROR", null));
            });
    }

    public record StateInfo(String state, OffsetDateTime updatedAt) {}

    // 상태 저장
    public Mono<Boolean> setState(String roomId, String state) {
        log.info("[DB] {} 방 상태 저장: {}", roomId, state);
        return chatRoomRepository.updateState(UUID.fromString(roomId), state)
            .map(count -> count > 0)
            .timeout(Duration.ofSeconds(2))
            .onErrorReturn(false);
    }

    // 상태 초기화
    public Mono<Boolean> clearState(String roomId) {
        log.info("[DB] {} 방 상태 초기화", roomId);
        return chatRoomRepository.clearState(UUID.fromString(roomId))
            .map(count -> count > 0)
            .timeout(Duration.ofSeconds(2))
            .onErrorReturn(false);
    }
}