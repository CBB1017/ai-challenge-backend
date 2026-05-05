package com.brycenkorea.template.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class BoardScheduler {

    private final BoardService boardService;
    private final JobScheduler jobScheduler;

    @PostConstruct
    public void init() {
        // 서버 기동 시 Redis에 데이터가 없는 경우 즉시 한 번 실행
        log.info("Starting initial board posts check...");
        boardService.getBoardAndRecentPosts()
            .flatMap(posts -> {
                boolean isEffectivelyEmpty = posts.isEmpty() || 
                    (posts.get("recent") == null || posts.get("recent").isEmpty()) && 
                    posts.entrySet().stream()
                        .filter(e -> !e.getKey().equals("recent"))
                        .allMatch(e -> e.getValue().isEmpty());
                
                if (isEffectivelyEmpty) {
                    log.info("No board posts found in Redis or all categories are empty, fetching from crawler...");
                    return boardService.fetchAndSaveAllBoardData(false);
                }
                log.info("Board posts already exist in Redis.");
                return Mono.empty();
            })
            .doOnSuccess(v -> log.info("Initial board posts check completed"))
            .doOnError(error -> log.error("Initial board posts fetch failed", error))
            .subscribe();

        // 30분마다 실행되는 스케줄 등록 (JobRunr)
        jobScheduler.scheduleRecurrently("30min-board-posts-fetch", "*/30 * * * *", boardService::fetchAndSaveAllBoardDataBlocking);
        log.info("30-minute board posts fetch job scheduled.");
    }
}
