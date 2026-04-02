package com.brycenkorea.template.service;

import com.brycenkorea.template.entity.Attendance;
import com.brycenkorea.template.mapper.mapstruct.AttendanceMapper;
import com.brycenkorea.template.repository.AttendanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
    @Transactional // R2DBC용 TransactionalManager가 설정되어 있어야 합니다.
    public Mono<Void> saveOrUpdateAttendanceList(List<Attendance> attendanceList) {
        if (attendanceList == null || attendanceList.isEmpty()) {
            return Mono.empty();
        }

        // 1. 검색 조건 생성 (Set)
        Set<String> nameSet = attendanceList.stream().map(Attendance::getMemberName).collect(Collectors.toSet());
        Set<String> positionSet = attendanceList.stream().map(Attendance::getPosition).collect(Collectors.toSet());

        // 2. 기존 데이터 조회 및 Map 변환
        return attendanceRepository.findAllByAttendanceDtAndMemberNameInAndPositionIn(
                getTodayDate(),
                nameSet,
                positionSet
            )
            .collectMap(a -> a.getMemberName() + "::" + a.getPosition()) // Flux를 Mono<Map>으로 변환
            .flatMap(attendanceMap -> {
                // 3. 비동기 처리 루프 (Flux.fromIterable)
                return Flux.fromIterable(attendanceList).flatMap(newEntity -> {
                    String key = newEntity.getMemberName() + "::" + newEntity.getPosition();
                    Attendance oldEntity = attendanceMap.get(key);

                    if (oldEntity != null) {
                        // 기존 데이터가 있으면 매핑 후 업데이트
                        attendanceMapper.updateEntity(oldEntity, newEntity);
                        return attendanceRepository.save(oldEntity);
                    } else {
                        // 없으면 신규 저장
                        return attendanceRepository.save(newEntity);
                    }
                }).then(); // 모든 저장이 끝나면 Mono<Void> 반환
            });
    }

    /**
     * 오늘 날짜 반환 (yyyy-MM-dd)
     */
    public String getTodayDate() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
}
