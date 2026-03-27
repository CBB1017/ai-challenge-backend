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

    // 예약 시에는 LLM에게 jobId를 받지 않고, 백엔드에서 자체 생성합니다.
    public record ScheduleRequest(String targetTime, String action, Map<String, Object> params) {}

    // 취소 시에는 LLM이 기억하고 있는 UUID 문자열을 받습니다.
    public record CancelRequest(String jobId) {}

    @Tool(description = "사용자의 요청에 따라 특정 시간(예: 2026-03-28T09:00:00)에 작업을 예약합니다. 성공 시 발급되는 UUID 형태의 작업 ID를 반드시 기억하세요.")
    public String scheduleAction(ScheduleRequest request) {
        try {
            LocalDateTime time = LocalDateTime.parse(request.targetTime());

            // 1. 고유한 UUID를 기반으로 JobId 객체 생성
            JobId newJobId = new JobId(UUID.randomUUID());

            // 2. 서비스 호출
            return schedulerService.scheduleOneTimeAction(newJobId, time, request.action(), request.params());
            // 반환값 예: "2026-03-28T09:00에 스케줄이 성공적으로 예약되었습니다. (ID: 550e8400-e29b-41d4-a716-446655440000)"
        } catch (Exception e) {
            return "스케줄 예약 실패: " + e.getMessage();
        }
    }

    @Tool(description = "기존에 예약된 스케줄을 작업 ID(UUID)를 이용해 취소/삭제합니다.")
    public String cancelAction(CancelRequest request) {
        try {
            // 1. LLM이 넘겨준 UUID 문자열을 JobId 객체로 파싱 (작성하신 static parse 메서드 활용!)
            JobId jobId = JobId.parse(request.jobId());

            // 2. 서비스 호출
            return schedulerService.cancelSchedule(jobId);
        } catch (IllegalArgumentException e) {
            return "스케줄 취소 실패: 유효하지 않은 UUID 형식입니다.";
        } catch (Exception e) {
            return "스케줄 취소 실패: " + e.getMessage();
        }
    }
}