package com.brycenkorea.template.service;

import com.brycenkorea.template.tools.PythonCrawlerTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class GeminiService {

    private final ChatClient chatClient;

    // ChatClient.Builder와 방금 만든 Tools를 주입받습니다.
    public GeminiService(ChatClient.Builder builder, PythonCrawlerTools crawlerTools) {
        this.chatClient = builder
            // 1. AI의 기본 성격(System Prompt) 부여
            .defaultSystem("너는 우리 회사의 친절하고 똑똑한 AI 비서야. 사내 정보가 필요하면 반드시 제공된 도구를 사용해서 확인한 뒤 답변해줘.")
            // 2. 도구 장착! (M2 버전의 핵심)
            .defaultTools(crawlerTools)
            .build();
    }

    public String ask(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt must not be empty");
        }

        // 코드가 믿을 수 없을 만큼 간결합니다.
        return chatClient.prompt()
                         .user(prompt)
                         .call()
                         .content();
    }
}