package com.brycenkorea.template.dto.response;

import com.brycenkorea.template.entity.ChatRoom;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatRoomResponse(UUID roomId, String title, OffsetDateTime updatedAt) {
    public static ChatRoomResponse from(ChatRoom entity) {
        return new ChatRoomResponse(entity.getRoomId(), entity.getTitle(), entity.getUpdatedAt());
    }
}