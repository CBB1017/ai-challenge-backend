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
                                                                                                      .system(s -> s.param(
                                                                                                                        "userId",
                                                                                                                        userId
                                                                                                                    )
                                                                                                                    .param(
                                                                                                                        "userDept",
                                                                                                                        userDept
                                                                                                                    )
                                                                                                                    .param(
                                                                                                                        "userName",
                                                                                                                        userName
                                                                                                                    )
                                                                                                                    .param(
                                                                                                                        "context",
                                                                                                                        currentSOP.rules()
                                                                                                                    ))
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
                                                Mono<ChatMessage> saveUserMsg = chatMessageRepository.save(ChatMessage.builder()
                                                                                                                      .roomId(
                                                                                                                          UUID.fromString(
                                                                                                                              roomId))
                                                                                                                      .role(
                                                                                                                          "USER")
                                                                                                                      .content(
                                                                                                                          prompt)
                                                                                                                      .build());

                                                // 💡 2. LLM 스트리밍 수행: 블로킹 방지를 위해 명시적으로 boundedElastic 할당
                                                Flux<ChatResponse> aiStream = Flux.defer(() -> finalSpec.stream()
                                                                                                        .chatResponse())
                                                                                  .subscribeOn(Schedulers.boundedElastic()) // 👈 여기에 추가! (블로킹 격리)
                                                                                  .doOnNext(response -> {
                                                                                      if (response.getResult() != null && response.getResult()
                                                                                                                                  .getOutput()
                                                                                                                                  .getText() != null)
                                                                                      {
                                                                                          aiResponseBuffer.append(
                                                                                              response.getResult()
                                                                                                      .getOutput()
                                                                                                      .getText());
                                                                                      }
                                                                                  });

                                                return saveUserMsg.thenMany(aiStream)
                                                                  .concatWith(Mono.defer(() -> { // 💡 Flux.defer -> Mono.defer로 변경 (응답을 내보내지 않는 후속 작업이므로)
                                                                      return chatMessageRepository.save(ChatMessage.builder()
                                                                                                                   .roomId(
                                                                                                                       UUID.fromString(
                                                                                                                           roomId))
                                                                                                                   .role(
                                                                                                                       "ASSISTANT")
                                                                                                                   .content(
                                                                                                                       aiResponseBuffer.toString())
                                                                                                                   .build())
                                                                                                  .doOnSuccess(savedMsg -> {
                                                                                                      generateAndSaveRoomTitle(
                                                                                                          roomId,
                                                                                                          prompt,
                                                                                                          aiResponseBuffer.toString()
                                                                                                      ).subscribeOn(
                                                                                                           Schedulers.boundedElastic())
                                                                                                       .subscribe();
                                                                                                  })
                                                                                                  .then(Mono.empty());
                                                                  }));
                                            });
    }

    // 💡 제목 요약 전용 비동기 메서드 (동일 클래스 내 추가)
    private Mono<?> generateAndSaveRoomTitle(String roomId, String userMsg, String aiMsg) {
        String summaryPrompt = String.format("다음 대화를 바탕으로 채팅방의 제목을 15자 이내로 요약해줘.\n유저: %s\nAI: %s", userMsg, aiMsg);

        // 💡 Callable 내부의 블로킹 코드를 명시적으로 boundedElastic 스레드에서 실행
        return Mono.fromCallable(() -> baseChatClient.prompt().user(summaryPrompt).call().content())
                   .subscribeOn(Schedulers.boundedElastic()) // 👈 이 부분 중요
                   .flatMap(title -> chatRoomRepository.updateTitle(UUID.fromString(roomId), title))
                   .onErrorResume(e -> {
                       log.error("방 제목 생성 실패", e);
                       return Mono.empty();
                   });
    }
}