package com.brycenkorea.template.entity;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Table("chat_message")
@Getter
@Builder
public class ChatMessage{

    @Id
    private UUID messageId;
    private UUID roomId;
    private String role;     // USER, ASSISTANT, TOOL 등
    private String content;
    private OffsetDateTime createdAt;
}