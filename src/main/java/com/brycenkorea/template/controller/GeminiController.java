package com.brycenkorea.template.controller;

import com.brycenkorea.template.dto.response.PromptRequest;
import com.brycenkorea.template.dto.response.PromptResponse;
import com.brycenkorea.template.service.GeminiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class GeminiController {

    private final GeminiService geminiService;

    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<PromptResponse> ask(
        @RequestBody @Valid PromptRequest promptRequest,
        @RequestParam(value = "mode", defaultValue = "GENERAL") String mode,
        Authentication authentication
    ) {
        log.info("[{}] 요청 시작: {}", mode, authentication.getName());


        // 2. 서비스 호출 및 로깅
        return geminiService.askStream(promptRequest.prompt(), mode, authentication.getName())
                            .map(PromptResponse::new)
                            .doOnNext(msg -> log.info("발송 중인 메시지: {}", msg.response()))
                            .doOnTerminate(() -> log.info("[{}] 스트림 종료", mode))
                            .doOnError(e -> log.error("스트리밍 에러: ", e));
    }
}