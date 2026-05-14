package com.brycenkorea.template.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.scheduling.cron.Cron;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class BirthdayScheduler {

    private final BirthdayService birthdayService;
    private final JobScheduler jobScheduler;
    private final SseBroadcaster sseBroadcaster;

    @PostConstruct
    public void init() {

        // 매일 자정에 실행되는 스케줄 등록 (JobRunr)
        jobScheduler.scheduleRecurrently("daily-birthday-fetch", Cron.daily(), this::scheduledBirthDataUpdate);
    }

    public void scheduledBirthDataUpdate() {
        Set<String> activeUsers = sseBroadcaster.getConnectedUserIds();
        log.info("스케줄러 실행: 접속 중인 유저 {}명 데이터 갱신", activeUsers.size());

        for (String userId : activeUsers) {
            // 접속 중인 유저 각각에 대해 크롤링 & MCP 호출 (블로킹 X)
            birthdayService.fetchAndSaveBirthdays(userId, true).subscribe();
        }
    }
}
