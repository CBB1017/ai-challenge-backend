package com.brycenkorea.template.controller;

import com.brycenkorea.template.dto.response.PromptRequest;
import com.brycenkorea.template.dto.response.PromptResponse;
import com.brycenkorea.template.service.GeminiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
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
        @RequestParam(value = "mode", defaultValue = "GENERAL") String mode
    ) {
        return ReactiveSecurityContextHolder.getContext()
                                            .map(SecurityContext::getAuthentication)
                                            .flatMapMany(auth -> {
                                                log.info("[{}] 요청 시작: {}", mode, auth.getName());

                                                return geminiService.askStream(promptRequest.prompt(), mode)
                                                                    .map(PromptResponse::new)
                                                                    // 각 청크가 나갈 때마다 인증 정보를 유지하도록 전파
                                                                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
                                            })
                                            .doOnTerminate(() -> log.info("[{}] 스트림 종료", mode))
                                            .doOnError(e -> log.error("스트리밍 에러: ", e));
    }
}