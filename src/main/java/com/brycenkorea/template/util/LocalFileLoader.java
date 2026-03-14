package com.brycenkorea.template.util;

import com.brycenkorea.template.config.DocumentLoaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Slf4j
public class LocalFileLoader implements CommandLineRunner {

    private final DocumentLoaderService documentLoaderService;

    @Override
    public void run(String... args) throws Exception {
        // 1. 적재할 파일이 있는 내 PC의 실제 경로
        String myLocalPath = "C:/Users/Moon/Downloads/brycenDocs";

        Path path = Paths.get(myLocalPath);
        if (!Files.exists(path)) {
            log.warn("❌ 경로가 존재하지 않습니다: {}", myLocalPath);
            return;
        }

        log.info("🚀 로컬 파일 적재 시작: {}", myLocalPath);

        // 2. 폴더 내 파일들을 순회하며 적재 (PDF, DOCX만 필터링)
        try (Stream<Path> paths = Files.walk(path)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".pdf") || p.toString().endsWith(".docx"))
                 .forEach(p -> {
                     try {
                         log.info("📄 처리 중: {}", p.getFileName());
                         documentLoaderService.loadDocument(new FileSystemResource(p));
                         log.info("✅ 적재 완료: {}", p.getFileName());
                     } catch (Exception e) {
                         log.error("❌ 적재 실패 [{}]: {}", p.getFileName(), e.getMessage());
                     }
                 });
        }

        log.info("🏁 모든 로컬 파일 적재 프로세스가 완료되었습니다.");
    }
}