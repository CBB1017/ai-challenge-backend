package com.brycenkorea.template.dto;

import com.brycenkorea.template.entity.ChatMessage;
import com.brycenkorea.template.entity.ChatRoom;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatMessageResponse(
    String role,
    String content,
    OffsetDateTime createdAt
) {

    public static ChatMessageResponse from(ChatMessage entity) {
        return new ChatMessageResponse(entity.getRole(), entity.getContent(), entity.getCreatedAt());
    }
}