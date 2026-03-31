package com.brycenkorea.template.service;

import com.brycenkorea.template.config.IntentRouter;
import com.brycenkorea.template.contants.AgentWorkflowSOP;
import com.brycenkorea.template.entity.ChatMessage;
import com.brycenkorea.template.repository.ChatMessageRepository;
import com.brycenkorea.template.repository.ChatRoomRepository;
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
    private final ChatRoomRepository chatRoomRepository;

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
                                                                                                      .advisors(a -> a.param(
                                                                                                          ChatMemory.CONVERSATION_ID,
                                                                                                          roomId
                                                                                                      ));

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

                                                return saveUserMsg.thenMany(aiStream)
                                                                  .concatWith(Flux.defer(() -> {
                                                                      // 1. AI 메시지 DB 저장
                                                                      return chatMessageRepository.save(
                                                                                                      ChatMessage.builder()
                                                                                                                 .roomId(UUID.fromString(roomId))
                                                                                                                 .role("ASSISTANT")
                                                                                                                 .content(aiResponseBuffer.toString())
                                                                                                                 .build()
                                                                                                  )
                                                                                                  // 💡 2. 메시지 저장이 끝나면 비동기로 제목 요약 로직 트리거
                                                                                                  .doOnSuccess(savedMsg -> {
                                                                                                      generateAndSaveRoomTitle(roomId, prompt, aiResponseBuffer.toString())
                                                                                                          .subscribeOn(Schedulers.boundedElastic())
                                                                                                          .subscribe(); // subscribe()를 호출하여 메인 스트림과 별개로 비동기 실행 (Fire & Forget)
                                                                                                  })
                                                                                                  .then(Mono.empty());
                                                                  }));
                                            })
                                            .subscribeOn(Schedulers.boundedElastic());
    }

    // 💡 제목 요약 전용 비동기 메서드 (동일 클래스 내 추가)
    private Mono<?> generateAndSaveRoomTitle(String roomId, String userMsg, String aiMsg) {
        String summaryPrompt = String.format(
            "다음 대화를 바탕으로 채팅방의 제목을 15자 이내로 요약해줘.\n유저: %s\nAI: %s",
            userMsg, aiMsg
        );

        return Mono.fromCallable(() -> baseChatClient.prompt().user(summaryPrompt).call().content())
                   .flatMap(title -> {
                       // chatRoomRepository를 사용하여 해당 roomId의 title 업데이트
                        return chatRoomRepository.updateTitle(UUID.fromString(roomId), title);
                   })
                   .onErrorResume(e -> {
                       log.error("방 제목 생성 실패", e);
                       return Mono.empty();
                   });
    }
}