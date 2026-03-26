package com.brycenkorea.template.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {
    private final ChatClient chatClient;
    private final ChatClient documentRetriever; // Config에서 등록한 빈 주입
    private final ChatMemory chatMemory; // Config에서 등록한 빈 주입

    public Flux<String> askStream(String prompt, String mode, String username) {
        if ("KNOWLEDGE".equalsIgnoreCase(mode)) {
            return documentRetriever.prompt().user(prompt).stream().content();
        }
        // 일반 모드
        return chatClient.prompt()
                         .advisors(new SimpleLoggerAdvisor())
                         .user(u -> u.text(prompt))
                         .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, username))
                         .stream()
                         .content();
    }
}