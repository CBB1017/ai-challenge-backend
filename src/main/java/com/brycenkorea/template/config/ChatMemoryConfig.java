package com.brycenkorea.template.config;

import com.brycenkorea.template.repository.RedisChatMemoryRepository;
import com.brycenkorea.template.tools.PythonCrawlerTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatMemoryConfig {
    @Bean
    public ChatMemory chatMemory(RedisChatMemoryRepository redisChatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                                      .chatMemoryRepository(redisChatMemoryRepository)
                                      .maxMessages(20) // 최신 20개 메시지만 기억
                                      .build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, PythonCrawlerTools crawlerTools, ChatMemory chatMemory) {
        return builder.defaultSystem("너는 우리 회사의 친절하고 똑똑한 AI 비서야. 사내 정보가 필요하면 반드시 제공된 도구를 사용해서 확인한 뒤 답변해줘.")
                      .defaultTools(crawlerTools)
                      .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                      .build();
    }
}