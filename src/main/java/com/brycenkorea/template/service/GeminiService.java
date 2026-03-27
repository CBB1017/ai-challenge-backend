package com.brycenkorea.template.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private final ChatClient baseChatClient;
    private final RetrievalAugmentationAdvisor ragAdvisor;

    public Flux<String> askStream(String prompt, String mode, String username) {

        ChatClient.ChatClientRequestSpec spec = baseChatClient.prompt()
                                                              .user(prompt)
                                                              .advisors(a -> a.param(
                                                                  ChatMemory.CONVERSATION_ID,
                                                                  username
                                                              ));

        if ("KNOWLEDGE".equalsIgnoreCase(mode)) {
            spec.advisors(ragAdvisor);
        }
        // Flux.defer()로 감싸서, chatClient.prompt() 설정 자체를 구독(subscribe) 시점까지 미룹니다.
        // 그리고 이 지연된 작업을 BoundedElastic 스레드 풀에서 실행하도록 지정합니다.
        return Flux.defer(() -> spec.stream().content()).subscribeOn(Schedulers.boundedElastic());
    }
}