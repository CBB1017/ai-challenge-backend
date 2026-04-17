package com.brycenkorea.template.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Table("chat_room")
@Getter
@Builder(toBuilder = true)
@ToString
public class ChatRoom {

    @Id
    private UUID roomId;  // UUID 타입은 R2DBC PostgreSQL 드라이버가 자동 매핑해줍니다.
    private String userId;
    private String title;
    private String state;
    private OffsetDateTime stateUpdatedAt;
    // DB에서 기본값을 세팅하므로 Insert 시 null로 넘겨도 무방합니다.
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}