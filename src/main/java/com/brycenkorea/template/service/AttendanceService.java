package com.brycenkorea.template.service;

import com.brycenkorea.template.entity.Attendance;
import com.brycenkorea.template.mapper.mapstruct.AttendanceMapper;
import com.brycenkorea.template.repository.AttendanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {
    private final AttendanceRepository attendanceRepository;
    private final AttendanceMapper attendanceMapper;

    /**
     * 팀별 근태 데이터를 저장(업데이트 또는 추가)한다.
     * - 이미 오늘 데이터가 있으면 업데이트, 없으면 새로 저장.
     */
    @Transactional
    public void saveOrUpdateAttendanceList(List<Attendance> attendanceList) {
        // 오늘날짜, 이름+직급 Map 생성
        Set<String> nameSet = attendanceList.stream().map(a -> a.getMember().getName()).collect(Collectors.toSet());
        Set<String> positionSet = attendanceList.stream()
                                                .map(a -> a.getMember().getPosition())
                                                .collect(Collectors.toSet());

        // 오늘자 출결 Map
        Map<String, Attendance> attendanceMap = attendanceRepository.findAllByAttendanceDtAndMemberNameInAndMemberPositionIn(
            getTodayDate(),
            nameSet,
            positionSet
        ).stream().collect(Collectors.toMap(a -> a.getMember().getName() + "::" + a.getMember().getPosition(), a -> a));

        for (Attendance entity : attendanceList) {
            String key = entity.getMember().getName() + "::" + entity.getMember().getPosition();
            Attendance old = attendanceMap.get(key);
            if (old != null) {
                attendanceMapper.updateEntity(old, entity);
            } else {
                attendanceRepository.save(entity);
            }
        }
    }

    /**
     * 오늘 날짜 반환 (yyyy-MM-dd)
     */
    public String getTodayDate() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
}
