package com.brycenkorea.template.service;


import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.JobId;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Slf4j
@Service
public class AttendanceSchedulerService {

    private final JobScheduler jobScheduler;
    private final WebClient webClient;

    public AttendanceSchedulerService(JobScheduler jobScheduler, WebClient pythonCrawlerWebClient) {
        this.jobScheduler = jobScheduler;
        this.webClient = pythonCrawlerWebClient;
    }

    // 1. 예약 메서드 (SchedulerAiTools에서 호출됨)
    public String scheduleOneTimeAction(
        JobId jobId,
        LocalDateTime targetTime,
        String action,
        Map<String, Object> params
    )
    {
        jobScheduler.schedule(
            jobId.asUUID(),
            targetTime.atZone(ZoneId.systemDefault()).toOffsetDateTime(),
            () -> this.executeScheduledTask(action, params) // 예약된 시간에 실행할 메서드
        );
        log.info("스케줄 등록 완료: [{}] 시간에 [{}] 실행", targetTime, action);
        return targetTime + "에 스케줄이 성공적으로 예약되었습니다. (ID: " + jobId + ")";
    }

    // 2. 취소 메서드
    public String cancelSchedule(JobId jobId) {
        jobScheduler.delete(jobId.asUUID());
        log.info("스케줄 삭제 완료: [{}]", jobId);
        return "스케줄이 취소되었습니다.";
    }

    // 3. JobRunr가 시간이 되면 실행하는 실제 작업 (MCP 대신 직접 API 호출)
    public void executeScheduledTask(String action, Map<String, Object> params) {
        log.info("예약된 작업 실행됨 -> Python 크롤러 호출: {}", action);

        try {
            // PythonCrawlerTools에서 쓰신 것과 동일한 방식!
            String response = webClient.post()
                                        .uri("/crawling/{action}", action)
                                        .bodyValue(params)
                                        .retrieve()
                                        .bodyToMono(String.class)
                                        .block();
            log.info("크롤러 실행 결과: {}", response);
            // TODO: 결과를 슬랙으로 보내거나 처리하는 로직 추가

        } catch (Exception e) {
            log.error("크롤러 실행 중 에러 발생", e);
        }
    }
}