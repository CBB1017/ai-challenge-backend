package com.brycenkorea.template.service;

import com.brycenkorea.template.config.IntentRouter;
import com.brycenkorea.template.contants.AgentWorkflowSOP;
import com.brycenkorea.template.entity.ChatMessage;
import com.brycenkorea.template.repository.ChatMessageRepository;
import com.brycenkorea.template.security.GroupwareAuthenticationToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private final ChatClient baseChatClient;
    private final RetrievalAugmentationAdvisor ragAdvisor;
    private final IntentRouter intentRouter;
    private final ChatMessageRepository chatMessageRepository;

    public Flux<ChatResponse> askStream(String prompt, String roomId) {
        return ReactiveSecurityContextHolder.getContext()
                                            .mapNotNull(SecurityContext::getAuthentication)
                                            .cast(GroupwareAuthenticationToken.class)
                                            .flatMapMany(auth -> {
                                                String userId = auth.getName();
                                                String userDept = auth.getDepartment();
                                                String userName = auth.getName();

                                                AgentWorkflowSOP currentSOP = intentRouter.classify(prompt);

                                                ChatClient.ChatClientRequestSpec spec = baseChatClient.prompt()
                                                                                                      .system(s -> s.param("userId", userId)
                                                                                                                    .param("userDept", userDept)
                                                                                                                    .param("userName", userName)
                                                                                                                    .param("context", currentSOP.rules()))
                                                                                                      .user(prompt)
                                                                                                      .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, roomId.toString()));

                                                if (currentSOP.requiresRag()) {
                                                    spec = spec.advisors(ragAdvisor);
                                                }

                                                final ChatClient.ChatClientRequestSpec finalSpec = spec;
                                                StringBuilder aiResponseBuffer = new StringBuilder();

                                                // 1. 유저 메시지 저장 (Mono)
                                                Mono<ChatMessage> saveUserMsg = chatMessageRepository.save(
                                                    ChatMessage.builder().roomId(UUID.fromString(roomId)).role("USER").content(prompt).build()
                                                );

                                                // 2. LLM 스트리밍 수행 (Flux)
                                                Flux<ChatResponse> aiStream = Flux.defer(() -> finalSpec.stream().chatResponse())
                                                                                  .doOnNext(response -> {
                                                                                      if (response.getResult() != null) {
                                                                                          aiResponseBuffer.append(
                                                                                              response.getResult()
                                                                                                      .getOutput()
                                                                                                      .getText());
                                                                                      }
                                                                                  });

                                                // 3. [핵심] 모든 과정을 체이닝
                                                return saveUserMsg.thenMany(aiStream)
                                                                  .concatWith(Flux.defer(() -> {
                                                                      // 스트리밍이 다 끝난 시점에 실행됨
                                                                      // 저장이 완료될 때까지 기다리도록 Mono를 Flux로 변환하여 병합
                                                                      return chatMessageRepository.save(
                                                                          ChatMessage.builder()
                                                                                     .roomId(UUID.fromString(roomId))
                                                                                     .role("ASSISTANT")
                                                                                     .content(aiResponseBuffer.toString())
                                                                                     .build()
                                                                      ).then(Mono.empty()); // UI에는 아무것도 추가로 보내지 않음
                                                                  }));
                                            })
                                            .subscribeOn(Schedulers.boundedElastic());
    }
}