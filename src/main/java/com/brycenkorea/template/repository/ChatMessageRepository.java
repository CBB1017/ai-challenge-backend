package com.brycenkorea.template.repository;

import com.brycenkorea.template.entity.ChatMessage;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface ChatMessageRepository extends ReactiveCrudRepository<ChatMessage, UUID> {

    // 2. 채팅방 입장 시: 해당 방의 과거 메시지 순서대로 불러오기
    Flux<ChatMessage> findByRoomIdOrderByCreatedAtAsc(UUID roomId);
}