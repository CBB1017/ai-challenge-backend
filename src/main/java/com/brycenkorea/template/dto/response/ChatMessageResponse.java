package com.brycenkorea.template.dto.response;

import com.brycenkorea.template.entity.ChatMessage;

import java.time.OffsetDateTime;

public record ChatMessageResponse(String role, String content, OffsetDateTime createdAt) {

    public static ChatMessageResponse from(ChatMessage entity) {
        return new ChatMessageResponse(entity.getRole(), entity.getContent(), entity.getCreatedAt());
    }
}