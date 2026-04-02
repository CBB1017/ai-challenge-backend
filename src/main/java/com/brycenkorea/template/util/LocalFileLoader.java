package com.brycenkorea.template.util;

import com.brycenkorea.template.config.DocumentLoaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@Service // @Component와 CommandLineRunner 제거
@RequiredArgsConstructor
@Slf4j
public class LocalFileLoader {
    private final DocumentLoaderService documentLoaderService;

    // 비블로킹 처리를 위해 Mono로 감싸고 스케줄러 할당
    public Mono<Void> ingestLocalFiles(String pathStr) {
        return Mono.fromRunnable(() -> {
            Path path = Paths.get(pathStr);
            try (Stream<Path> paths = Files.walk(path)) {
                paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".pdf") || p.toString().endsWith(".docx"))
                    .forEach(p -> documentLoaderService.loadDocument(new FileSystemResource(p)));
            } catch (Exception e) {
                log.error("파일 적재 에러", e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}