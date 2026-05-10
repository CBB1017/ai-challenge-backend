package com.brycenkorea.template.service;


import com.brycenkorea.template.contants.ActionStatus;
import com.brycenkorea.template.service.ActionService;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.JobId;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
public class AttendanceSchedulerService {

    private final JobScheduler jobScheduler;
    private final WebClient webClient;
    private final AsyncMcpToolCallbackProvider mcpToolCallbackProvider;
    private final JsonMapper jsonMapper;
    private final ActionService actionService;

    public AttendanceSchedulerService(JobScheduler jobScheduler,
                                     WebClient pythonCrawlerWebClient,
                                     AsyncMcpToolCallbackProvider mcpToolCallbackProvider,
                                     JsonMapper jsonMapper,
                                     ActionService actionService) {
        this.jobScheduler = jobScheduler;
        this.webClient = pythonCrawlerWebClient;
        this.mcpToolCallbackProvider = mcpToolCallbackProvider;
        this.jsonMapper = jsonMapper;
        this.actionService = actionService;
    }

    // 1. 일회성 예약 메서드 (SchedulerAiTools에서 호출됨)
    public String scheduleOneTimeAction(
        JobId jobId,
        LocalDateTime targetTime,
        String action,
        Map<String, Object> params,
        String userId,
        UUID roomId
    )
    {
        jobScheduler.schedule(
            jobId.asUUID(),
            targetTime.atZone(ZoneId.systemDefault()).toOffsetDateTime(),
            () -> this.executeScheduledTask(action, params, userId, roomId)
        );
        log.info("일회성 스케줄 등록 완료: [{}] 시간에 [{}] 실행 (User: {})", targetTime, action, userId);
        return targetTime + "에 스케줄이 성공적으로 예약되었습니다. (ID: " + jobId + ")";
    }

    // 2. 반복성 예약 메서드
    public String scheduleRecurringAction(
        String action,
        Map<String, Object> params,
        String cronExpression,
        String userId,
        UUID roomId
    ) {
        JobId jobId = new JobId(UUID.randomUUID());
        jobScheduler.scheduleRecurrently(
            jobId.asUUID().toString(),
            cronExpression,
            () -> this.executeScheduledTask(action, params, userId, roomId)
        );
        log.info("반복 스케줄 등록 완료: [{}] 주기로 [{}] 실행 (User: {})", cronExpression, action, userId);
        return "스케줄이 성공적으로 예약되었습니다. (주기: " + cronExpression + ", ID: " + jobId + ")";
    }

    // 3. 취소 메서드
    public String cancelSchedule(JobId jobId) {
        // 일회성 및 반복성 모두 삭제 시도
        jobScheduler.delete(jobId.asUUID());
        log.info("스케줄 삭제 완료: [{}]", jobId);
        return "스케줄이 취소되었습니다.";
    }

    // 4. JobRunr가 시간이 되면 실행하는 실제 작업
    public void executeScheduledTask(String action, Map<String, Object> params, String userId, UUID roomId) {
        log.info("예약된 작업 실행됨: action={}, userId={}, roomId={}", action, userId, roomId);

        // 실행 로그 기록 (IN_PROGRESS)
        actionService.logAction(action, "Scheduled execution started", ActionStatus.IN_PROGRESS, userId, roomId).block();

        try {
            // 1. MCP 도구에서 먼저 검색
            Optional<ToolCallback> mcpTool = Arrays.stream(mcpToolCallbackProvider.getToolCallbacks())
                .filter(t -> t.getToolDefinition().name().equals(action))
                .findFirst();

            String result;
            if (mcpTool.isPresent()) {
                log.info("MCP 도구 실행: {}", action);
                String jsonParams = jsonMapper.writeValueAsString(params);
                result = mcpTool.get().call(jsonParams);
                log.info("MCP 도구 실행 결과: {}", result);
            } else {
                // 2. 없으면 기존 Python 크롤러 호출 (Legacy 지원)
                log.info("Legacy 크롤러 호출: {}", action);
                result = webClient.post()
                    .uri("/crawling/{action}", action)
                    .bodyValue(params)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
                log.info("크롤러 실행 결과: {}", result);
            }

            // 성공 로그 업데이트
            actionService.logAction(action, "Scheduled execution success: " + result, ActionStatus.SUCCESS, userId, roomId).block();

        } catch (Exception e) {
            log.error("예약 작업 실행 중 에러 발생", e);
            // 에러 로그 업데이트
            actionService.logAction(action, "Scheduled execution failed: " + e.getMessage(), ActionStatus.ERROR, userId, roomId).block();
        }
    }
}