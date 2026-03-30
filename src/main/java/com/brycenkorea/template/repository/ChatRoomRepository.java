package com.brycenkorea.template.repository;

import com.brycenkorea.template.contants.AttendanceStatus;
import com.brycenkorea.template.entity.ChatRoom;
import com.brycenkorea.template.entity.Notification;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import java.util.UUID;

public interface ChatRoomRepository extends ReactiveCrudRepository<ChatRoom, UUID> {

    // 1. 사이드바 로딩용: 특정 유저의 최근 채팅방 목록 조회
    Flux<ChatRoom> findByUserIdOrderByUpdatedAtDesc(String userId);
}