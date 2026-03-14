package com.brycenkorea.template.controller;

import com.brycenkorea.template.config.DocumentLoaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentLoaderService documentLoaderService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<String> uploadDocument(@RequestPart("file") FilePart filePart) {
        // 1. 임시 파일 경로 생성
        Path tempFile = Path.of(
            System.getProperty("java.io.tmpdir"),
            "upload_" + System.currentTimeMillis() + "_" + filePart.filename()
        );

        return filePart.transferTo(tempFile) // 비차단 파일 저장
                       .then(Mono.fromRunnable(() -> {
                                     log.info("블로킹 적재 작업 시작: {}", filePart.filename());
                                     try {
                                         // 2. 서비스 호출 (기존 블로킹 로직)
                                         documentLoaderService.loadDocument(new FileSystemResource(tempFile));
                                     } finally {
                                         // 3. 리소스 정리
                                         cleanup(tempFile);
                                     }
                                 })
                                 // 💡 핵심: 블로킹 작업을 전용 스레드 풀(boundedElastic)로 격리
                                 .subscribeOn(Schedulers.boundedElastic()))
                       .map(v -> "✅ 적재 완료: " + filePart.filename())
                       .onErrorResume(e -> {
                           log.error("적재 중 에러 발생: {}", e.getMessage());
                           cleanup(tempFile);
                           return Mono.just("❌ 적재 실패: " + e.getMessage());
                       });
    }

    private void cleanup(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("임시 파일 삭제 실패: {}", path);
        }
    }
}