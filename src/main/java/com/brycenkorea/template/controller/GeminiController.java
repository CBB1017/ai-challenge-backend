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
        @RequestBody @Valid PromptRequest promptRequest
    ) {

        // 2. 서비스 호출 및 로깅
        return geminiService.askStream(promptRequest.prompt(), promptRequest.roomId())
                            .filter(chatResponse -> {
                                // 1. 응답 결과(Generation)들 중에 Tool Call이 하나라도 있으면 사용자에게 보내지 않음
                                return chatResponse.getResults().stream()
                                                   .noneMatch(generation -> generation.getOutput().hasToolCalls()
                                                   );
                            })
                            .map(chatResponse -> {
                                // 2. 텍스트 내용만 추출 (null 체크 포함)
                                String content = chatResponse.getResult() != null
                                    ? chatResponse.getResult().getOutput().getText()
                                    : "";
                                return new PromptResponse(content);
                            })
                            .filter(resp -> !resp.response().isEmpty()) // 빈 메시지는 전송 안 함
                            .doOnNext(msg -> log.info("발송 중인 메시지: {}", msg.response()))
                            .doOnTerminate(() -> log.info("[{}] 스트림 종료", promptRequest.roomId()))
                            .doOnError(e -> log.error("스트리밍 에러: ", e));
    }
}