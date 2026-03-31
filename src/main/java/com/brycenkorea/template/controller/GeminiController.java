package com.brycenkorea.template.controller;

import com.brycenkorea.template.dto.response.PromptRequest;
import com.brycenkorea.template.dto.response.PromptResponse;
import com.brycenkorea.template.service.GeminiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class GeminiController {

    private final GeminiService geminiService;

    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<PromptResponse> ask(@RequestBody @Valid PromptRequest promptRequest) {
        log.info("[{}] 요청 시작 - Prompt: {}", promptRequest.roomId(), promptRequest.prompt());

        return geminiService.askStream(promptRequest.prompt(), promptRequest.roomId())
                            .doOnSubscribe(s -> log.info("Gemini 스트림 구독 시작")) // 구독 여부 확인
                            .doOnNext(res -> log.info("Raw 데이터 수신: {}", res))    // 데이터 도달 확인
                            .filter(chatResponse -> {
                                boolean hasTool = chatResponse.getResults()
                                                              .stream()
                                                              .anyMatch(gen -> gen.getOutput().hasToolCalls());
                                if (hasTool) {
                                    log.info("Tool Call 발견으로 필터링됨");
                                }
                                return !hasTool;
                            })
                            .map(chatResponse -> {
                                String content = (chatResponse.getResult() != null) ? chatResponse.getResult()
                                                                                                  .getOutput()
                                                                                                  .getText() : "";
                                return new PromptResponse(content);
                            })
                            .filter(resp -> {
                                boolean isEmpty = resp.response().isEmpty();
                                if (isEmpty) {
                                    log.debug("빈 메시지 스킵");
                                }
                                return !isEmpty;
                            })
                            .doOnNext(msg -> log.info("최종 발송 데이터: {}", msg.response()))
                            .doOnTerminate(() -> log.info("스트림 정상 종료"))
                            .doOnError(e -> log.error("스트리밍 에러 발생: ", e))
                            .switchIfEmpty(Flux.defer(() -> {
                                log.warn("전송할 데이터가 하나도 없습니다 (Empty Flux)");
                                return Flux.empty();
                            }));
    }
}