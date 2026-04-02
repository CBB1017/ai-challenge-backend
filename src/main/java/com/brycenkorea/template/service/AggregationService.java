package com.brycenkorea.template.service;

import com.brycenkorea.template.dto.AggregationDto;
import com.brycenkorea.template.entity.Attendance;
import com.brycenkorea.template.repository.AttendanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.*;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AggregationService {

    private final AttendanceRepository attendanceRepository;

    public Mono<AggregationDto> getAggregatedDailyDataByDate(String date) {
        LocalDateTime startOfDay = getStartOfDay(date);
        LocalDateTime endOfDay = getEndOfDay(date);

        return attendanceRepository.findAllByCreatedAtBetween(startOfDay, endOfDay)
            .filter(this::isWeekday) // Flux 내부에서 필터링
            .collectList()           // Flux를 Mono<List>로 변환
            .map(this::makeAggregationDto);
    }

    public Mono<AggregationDto> getAggregatedPeriodDataByDate(String start, String end) {
        LocalDateTime startOfDay = getStartOfDay(start);
        LocalDateTime endOfDay = getEndOfDay(end);

        return attendanceRepository.findAllByCreatedAtBetween(startOfDay, endOfDay)
            .filter(this::isWeekday)
            .collectList()
            .map(this::makeAggregationDto);
    }

    public Mono<AggregationDto> getAggregatedMonthlyDataByDate(String date) {
        // date 형식이 "yyyy-MM"일 경우 처리
        LocalDateTime startOfMonth = getStartOfDay(date + "-01");
        LocalDateTime endOfMonth = getEndOfMonth(date);

        return attendanceRepository.findAllByCreatedAtBetween(startOfMonth, endOfMonth)
            .filter(this::isWeekday)
            .collectList()
            .map(this::makeAggregationDto);
    }

    private AggregationDto makeAggregationDto(List<Attendance> attendances) {
        AggregationDto result = new AggregationDto();

        // 1. 출근 미기입자 (필터 로직 최적화)
        result.setMissingStart(attendances.stream()
            .filter(v -> !isExceptionType(v.getActualType()) && StringUtils.isBlank(v.getActualIn()))
            .map(Attendance::getMemberName)
            .toList());

        // 2. 퇴근 미기입자
        result.setMissingEnd(attendances.stream()
            .filter(v -> !isExceptionType(v.getActualType()) && StringUtils.isBlank(v.getActualOut()))
            .map(Attendance::getMemberName)
            .toList());

        result.setMissingPlan(Collections.emptyList());
        return result;
    }

    // 예외 근무 타입 체크 (가독성 향상)
    private boolean isExceptionType(String type) {
        return "연차".equals(type) || "보상종일".equals(type);
    }

    private LocalDateTime getStartOfDay(String dateStr) {
        return LocalDate.parse(dateStr).atStartOfDay();
    }

    private LocalDateTime getEndOfDay(String dateStr) {
        return LocalDate.parse(dateStr).atTime(LocalTime.MAX);
    }

    private LocalDateTime getEndOfMonth(String yearMonthStr) {
        YearMonth yearMonth = YearMonth.parse(yearMonthStr);
        return yearMonth.atEndOfMonth().atTime(LocalTime.MAX);
    }

    private boolean isWeekday(Attendance attendance) {
        LocalDate date = LocalDate.parse(attendance.getAttendanceDt());
        DayOfWeek dow = date.getDayOfWeek();

        // 평일이거나, 주말인데 출근 기록이 있는 경우
        return (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) || StringUtils.isNotBlank(attendance.getActualIn());
    }
}