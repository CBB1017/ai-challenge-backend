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
        log.info("[{}] 요청 시작", promptRequest.roomId());

        return geminiService.askStreamProcessed(promptRequest.prompt(), promptRequest.roomId())
            .doOnSubscribe(s -> log.info("Gemini 스트림 구독 시작"))
            .doOnTerminate(() -> log.info("스트림 정상 종료"))
            .doOnError(e -> log.error("스트리밍 에러 발생: ", e))
            .switchIfEmpty(Flux.defer(() -> {
                log.warn("전송할 데이터가 없음");
                return Flux.empty();
            }));
    }
}