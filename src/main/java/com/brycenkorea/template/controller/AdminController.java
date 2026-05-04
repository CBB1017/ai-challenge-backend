package com.brycenkorea.template.controller;

import com.brycenkorea.template.util.LocalFileLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {
    private final LocalFileLoader localFileLoader;

    @PostMapping("/ingest")
    public Mono<String> triggerIngest() {
        // 수동으로 트리거할 때만 적재 시작
        return localFileLoader.ingestLocalFiles("C:/Users/Moon/Documents/brycenDocs")
            .thenReturn("✅ 적재 프로세스가 백그라운드에서 시작되었습니다.");
    }
}