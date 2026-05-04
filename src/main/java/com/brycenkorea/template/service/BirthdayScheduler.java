package com.brycenkorea.template.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.scheduling.cron.Cron;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BirthdayScheduler {

    private final BirthdayService birthdayService;
    private final JobScheduler jobScheduler;

    @PostConstruct
    public void init() {
        // 서버 기동 시 즉시 한 번 실행
        log.info("Starting initial birthday data fetch...");
        birthdayService.fetchAndSaveBirthdays()
            .doOnSuccess(v -> log.info("Initial birthday fetch completed"))
            .doOnError(error -> log.error("Initial birthday fetch failed", error))
            .subscribe();

        // 매일 자정에 실행되는 스케줄 등록 (JobRunr)
        jobScheduler.scheduleRecurrently("daily-birthday-fetch", Cron.daily(), birthdayService::fetchAndSaveBirthdaysBlocking);
        log.info("Daily birthday fetch job scheduled.");
    }
}
