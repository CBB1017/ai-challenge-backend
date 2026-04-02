package com.brycenkorea.template.util;

import com.brycenkorea.template.config.DocumentLoaderService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentFolderWatcher {

    private final DocumentLoaderService documentLoaderService;

    @Value("${app.watch-path:./ingest}")
    private String watchPath;

    @PostConstruct
    public void startScanning() {
        // 💡 10초마다 스캔 작업을 생성
        Flux.interval(Duration.ofSeconds(10))
            .flatMap(i ->
                // 1. 파일 목록을 읽는 작업 자체를 별도 스레드로 격리
                Mono.fromCallable(() -> {
                        Path path = Paths.get(watchPath);
                        if (!Files.exists(path)) {
                            Files.createDirectories(path);
                        }
                        return Files.list(path);
                    }).subscribeOn(Schedulers.boundedElastic()) // I/O 전용 스레드 사용
                    .flatMapMany(Flux::fromStream)             // Stream을 Flux로 변환
            )
            .filter(path -> !Files.isDirectory(path))
            .flatMap(path ->
                // 2. 각 파일의 처리(적재)도 비동기로 실행
                processFile(path).subscribeOn(Schedulers.boundedElastic()))
            .doOnError(e -> log.error("폴더 감시 중 에러 발생: {}", e.getMessage()))
            .subscribe();
    }

    private Mono<Void> processFile(Path path) {
        return Mono.fromRunnable(() -> {
            log.info("새 파일 감지 및 적재 시작: {}", path.getFileName());
            documentLoaderService.loadDocument(new FileSystemResource(path));
            moveFileToArchived(path);
        }).then();
    }

    private void moveFileToArchived(Path path) {
        try {
            Path archiveDir = path.getParent().resolve("archived");
            if (!Files.exists(archiveDir)) {
                Files.createDirectories(archiveDir);
            }
            Files.move(path, archiveDir.resolve(path.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            log.error("파일 이동 실패: {}", e.getMessage());
        }
    }
}