package com.brycenkorea.template.repository;

import com.brycenkorea.template.entity.ChatRoom;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ChatRoomRepository extends ReactiveCrudRepository<ChatRoom, UUID> {

    // 사이드바 로딩용: 특정 유저의 최근 채팅방 목록 조회
    Flux<ChatRoom> findByUserIdOrderByUpdatedAtDesc(String userId);

    // 채팅방 제목 업데이트
    @Modifying
    @Query("UPDATE chat_room SET title = :title, updated_at = CURRENT_TIMESTAMP WHERE room_id = :roomId")
    Mono<Integer> updateTitle(UUID roomId, String title);

    // 채팅방 상태 업데이트
    @Modifying
    @Query("UPDATE chat_room SET state = :state, state_updated_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE room_id = :roomId")
    Mono<Integer> updateState(UUID roomId, String state);

    // 채팅방 상태 초기화
    @Modifying
    @Query("UPDATE chat_room SET state = NULL, state_updated_at = NULL, updated_at = CURRENT_TIMESTAMP WHERE room_id = :roomId")
    Mono<Integer> clearState(UUID roomId);
}