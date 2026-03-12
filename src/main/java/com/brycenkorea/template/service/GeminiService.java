package com.brycenkorea.template.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class GeminiService {
    private final ChatClient chatClient;

    // 반환 타입을 String에서 Flux<String>으로 변경
    public Flux<String> askStream(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt must not be empty");
        }
        var conversationId = "678";

        return chatClient.prompt()
                         .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                         .user(prompt)
                         .stream()
                         .content();
    }
}