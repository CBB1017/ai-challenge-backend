package com.brycenkorea.template.tools;

import com.brycenkorea.template.service.AttendanceSchedulerService;
import org.jobrunr.jobs.JobId;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
public class SchedulerAiTools {

    private final AttendanceSchedulerService schedulerService;

    public SchedulerAiTools(AttendanceSchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    @Tool(description = "사용자의 요청에 따라 특정 시간(예: 2026-03-28T09:00:00)에 1회성 작업을 예약합니다. 성공 시 발급되는 UUID 형태의 작업 ID를 반드시 기억하세요.")
    public String scheduleOneTimeAction(ScheduleRequest request, Map<String, Object> context) {
        try {
            LocalDateTime time = LocalDateTime.parse(request.targetTime());
            JobId newJobId = new JobId(UUID.randomUUID());

            String userId = (String) context.get("userId");
            String roomIdStr = (String) context.get("roomId");
            UUID roomId = roomIdStr != null ? UUID.fromString(roomIdStr) : null;

            return schedulerService.scheduleOneTimeAction(newJobId, time, request.action(), request.params(), userId, roomId);
        } catch (Exception e) {
            return "스케줄 예약 실패: " + e.getMessage();
        }
    }

    @Tool(description = "사용자의 요청에 따라 반복적인 작업을 예약합니다. 주기(frequency)는 'hourly', 'daily', 'weekly', 'monthly' 또는 직접적인 'cron' 표현식을 지원합니다.")
    public String scheduleRecurringAction(RecurringScheduleRequest request, Map<String, Object> context) {
        try {
            String cron = mapFrequencyToCron(request.frequency(), request.cron());
            String userId = (String) context.get("userId");
            String roomIdStr = (String) context.get("roomId");
            UUID roomId = roomIdStr != null ? UUID.fromString(roomIdStr) : null;

            return schedulerService.scheduleRecurringAction(request.action(), request.params(), cron, userId, roomId);
        } catch (Exception e) {
            return "반복 스케줄 예약 실패: " + e.getMessage();
        }
    }

    @Tool(description = "기존에 예약된 스케줄을 작업 ID(UUID)를 이용해 취소/삭제합니다.")
    public String cancelAction(CancelRequest request) {
        try {
            JobId jobId = JobId.parse(request.jobId());
            return schedulerService.cancelSchedule(jobId);
        } catch (IllegalArgumentException e) {
            return "스케줄 취소 실패: 유효하지 않은 UUID 형식입니다.";
        } catch (Exception e) {
            return "스케줄 취소 실패: " + e.getMessage();
        }
    }

    private String mapFrequencyToCron(String frequency, String customCron) {
        if ("cron".equalsIgnoreCase(frequency) && customCron != null) {
            return customCron;
        }
        return switch (frequency.toLowerCase()) {
            case "hourly" -> "0 * * * *";
            case "daily" -> "0 9 * * *";
            case "weekly" -> "0 9 * * 1";
            case "monthly" -> "0 9 1 * *";
            default -> throw new IllegalArgumentException("지원하지 않는 주기입니다: " + frequency);
        };
    }

    public record ScheduleRequest(String targetTime, String action, Map<String, Object> params) {}

    public record RecurringScheduleRequest(String frequency, String cron, String action, Map<String, Object> params) {}

    public record CancelRequest(String jobId) {}
}