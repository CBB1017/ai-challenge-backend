package com.brycenkorea.template.controller;

import com.brycenkorea.template.dto.response.PromptRequest;
import com.brycenkorea.template.dto.response.PromptResponse;
import com.brycenkorea.template.service.GeminiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class GeminiController {

    private final GeminiService geminiService;

    @PostMapping(
        value = "/ask",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<PromptResponse> ask(
        @RequestBody @Valid PromptRequest promptRequest,
        @RequestParam(value = "mode", defaultValue = "GENERAL") String mode
    ) {
        return geminiService.askStream(promptRequest.prompt(), mode)
                            .map(PromptResponse::new)
                            .doOnNext(response -> log.info("[{}] Chunk: {}", mode, response.response()));
    }
}