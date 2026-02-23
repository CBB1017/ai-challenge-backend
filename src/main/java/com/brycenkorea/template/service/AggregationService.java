package com.brycenkorea.template.service;

import com.brycenkorea.template.dto.AggregationDto;
import com.brycenkorea.template.entity.Attendance;
import com.brycenkorea.template.repository.AttendanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AggregationService {

    private final AttendanceRepository attendanceRepository;


    public AggregationDto getAggregatedDailyDataByDate(String date) {
        LocalDate parsedDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        DayOfWeek dayOfWeek = parsedDate.getDayOfWeek();

        LocalDateTime startOfDay = getStartOfDay(date);
        LocalDateTime endOfDay   = getEndOfDay(date);

        List<Attendance> attendances =
            attendanceRepository.findAllByCreatedAtBetween(startOfDay, endOfDay);
        List<Attendance> weekdays = attendances.stream()
                                               .filter(a -> isWeekday(a))
                                               .collect(Collectors.toList());
        return makeAggregationDto(weekdays);
    }

    public AggregationDto getAggregatedPeriodDataByDate(String start, String end) {
        LocalDateTime startOfDay = getStartOfDay(start);
        LocalDateTime endOfDay = getEndOfDay(end);

        List<Attendance> attendances = attendanceRepository.findAllByCreatedAtBetween(startOfDay, endOfDay);
        List<Attendance> weekdays = attendances.stream()
                                               .filter(a -> isWeekday(a))
                                               .collect(Collectors.toList());
        AggregationDto result = makeAggregationDto(weekdays);
        return result;
    }

    public AggregationDto getAggregatedMonthlyDataByDate(String date) {

        LocalDateTime startOfMonth = getStartOfDay(date + "-01");
        LocalDateTime endOfMonth = getEndOfMonth(date);

        List<Attendance> attendances = attendanceRepository.findAllByCreatedAtBetween(startOfMonth, endOfMonth);
        List<Attendance> weekdays = attendances.stream()
                                               .filter(a -> isWeekday(a))
                                               .collect(Collectors.toList());
        AggregationDto result = makeAggregationDto(weekdays);
        return result;
    }

    private AggregationDto makeAggregationDto(List<Attendance> attendances) {
        AggregationDto result = new AggregationDto();

        result.setMissingStart(attendances.stream().filter(v ->
        {
            boolean check = false;
            if(v.getActualType().equals("연차")) return false;
            if(v.getActualType().equals("보상종일")) return false;
            check = v.getActualIn().isEmpty();
            return check;
        }).map(Attendance::getMemberName).toList());
        result.setMissingEnd(attendances.stream().filter(v ->
        {
            boolean check = false;
            if(v.getActualType().equals("연차")) return false;
            if(v.getActualType().equals("보상종일")) return false;
            check = v.getActualOut().isEmpty();
            return check;
        }).map(Attendance::getMemberName).toList());
        result.setMissingPlan(List.of());
        return result;
    }

    private LocalDateTime getStartOfDay(String dateStr) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate date = LocalDate.parse(dateStr, formatter);
        return date.atStartOfDay(); // 00:00:00
    }

    private LocalDateTime getEndOfDay(String dateStr) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate date = LocalDate.parse(dateStr, formatter);
        return date.atTime(23, 59, 59, 999_999_999); // 23:59:59.999999999
    }

    private LocalDateTime getEndOfMonth(String yearMonthStr) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
        YearMonth yearMonth = YearMonth.parse(yearMonthStr, formatter);
        return yearMonth.atEndOfMonth().atTime(23, 59, 59, 999_999_999);
    }

    private boolean isWeekday(Attendance attendance) {
        // attendanceDt는 "yyyy-MM-dd" 형식이라고 가정
        LocalDate date = LocalDate.parse(attendance.getAttendanceDt(), DateTimeFormatter.ISO_LOCAL_DATE);
        DayOfWeek dow = date.getDayOfWeek();

        // 평일이면 true
        if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
            return true;
        }

        // 주말인데 actualIn(출근 시간) 값이 있으면 true
        String actualIn = attendance.getActualIn();
        return actualIn != null && !actualIn.isBlank();
    }
}
