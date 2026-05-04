package com.brycenkorea.template.controller;

import com.brycenkorea.template.dto.response.PromptRequest;
import com.brycenkorea.template.dto.response.PromptResponse;
import com.brycenkorea.template.service.GeminiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Objects;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Slf4j
public class GeminiController {

    private final GeminiService geminiService;

    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<PromptResponse> ask(
        @RequestBody @Valid PromptRequest promptRequest,
        Authentication authentication,
        ServerHttpResponse response
    ) {
        log.info("[{}] 요청 시작", promptRequest.roomId());
        String userId = Objects.requireNonNull(authentication.getPrincipal()).toString();

        return geminiService.getOrCreateRoom(promptRequest.roomId(), userId, promptRequest.language())
            .flatMapMany(roomId -> {
                // 1. 방 ID를 헤더에 추가
                response.getHeaders().add("X-Room-Id", roomId.toString());
                
                // 2. 의도(Intent) 분석을 먼저 수행하여 헤더에 추가
                return geminiService.determineIntent(promptRequest.prompt(), roomId.toString())
                    .flatMapMany(sop -> {
                        log.info("[{}] 결정된 의도: {}", roomId, sop.intentId());
                        response.getHeaders().add("X-Intent-Id", sop.intentId());
                        
                        // 3. 분석된 SOP를 기반으로 AI 스트림 실행
                        return geminiService.askStreamWithSop(promptRequest.prompt(), roomId.toString(), promptRequest.language(), sop);
                    });
            })
            .doOnSubscribe(s -> log.info("Gemini 스트림 구독 시작"))
            .doOnTerminate(() -> log.info("스트림 정상 종료"))
            .doOnError(e -> log.error("스트리밍 에러 발생: ", e))
            .switchIfEmpty(Flux.defer(() -> {
                log.warn("전송할 데이터가 없음");
                return Flux.empty();
            }));
    }
}