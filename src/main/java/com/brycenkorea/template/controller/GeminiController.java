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
        ServerWebExchange exchange // 💡 해결사: 가방 자체를 파라미터로 받음
    ) {
        // 1. 가방에서 인증 정보 꺼내기
        Authentication auth = exchange.getAttribute("SECURE_AUTH");

        if (auth == null) {
            log.error("인증 정보가 유실되었습니다.");
            return Flux.error(new IllegalStateException("Unauthorized"));
        }

        log.info("[{}] 요청 시작: {}", mode, auth.getName());

        // 2. 서비스 호출 및 로깅
        return geminiService.askStream(promptRequest.prompt(), mode)
                            .map(PromptResponse::new)
                            // 💡 GeminiService 내부나 Redis에서 인증 정보가 필요할 수 있으니 다시 묶어줌
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
                            .doOnNext(msg -> log.info("발송 중인 메시지: {}", msg.response()))
                            .doOnTerminate(() -> log.info("[{}] 스트림 종료", mode))
                            .doOnError(e -> log.error("스트리밍 에러: ", e));
    }
}