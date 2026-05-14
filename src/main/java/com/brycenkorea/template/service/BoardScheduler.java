package com.brycenkorea.template.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class BoardScheduler {

    private final BoardService boardService;
    private final JobScheduler jobScheduler;
    private final SseBroadcaster sseBroadcaster;

    @PostConstruct
    public void init() {
        jobScheduler.scheduleRecurrently("30min-board-posts-fetch", "*/30 * * * *", this::scheduledBoardDataUpdate);
        log.info("30-minute personalized board data fetch job scheduled.");
    }

    public void scheduledBoardDataUpdate() {
        Set<String> activeUsers = sseBroadcaster.getConnectedUserIds();
        log.info("스케줄러 실행: 접속 중인 유저 {}명 데이터 갱신", activeUsers.size());

        for (String userId : activeUsers) {
            // 접속 중인 유저 각각에 대해 크롤링 & MCP 호출 (블로킹 X)
            boardService.fetchAndSaveAllBoardData(userId, true).subscribe();
        }
    }
}