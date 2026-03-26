package com.brycenkorea.template.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private final ChatClient baseChatClient;
    private final RetrievalAugmentationAdvisor ragAdvisor;

    public Flux<String> askStream(String prompt, String mode, String username) {

        ChatClient.ChatClientRequestSpec spec = baseChatClient.prompt()
                                                              .user(prompt)
                                                              .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, username));

        if ("KNOWLEDGE".equalsIgnoreCase(mode)) {
            spec.advisors(ragAdvisor);
        }

        return spec.stream().content();
    }
}