package com.brycenkorea.template.repository;

import com.brycenkorea.template.entity.Attendance;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.Set;

public interface AttendanceRepository extends ReactiveCrudRepository<Attendance, Long> {

    // 1. 조건 검색 (Flux로 반환)
    // R2DBC에서도 메서드 이름 쿼리를 지원하지만, In 절과 다중 조건은 @Query가 더 확실합니다.
    @Query("""
        SELECT * FROM attendance 
        WHERE attendance_dt = :todayDate 
          AND member_name IN (:nameSet) 
          AND position IN (:positionSet)
    """)
    Flux<Attendance> findAllByAttendanceDtAndMemberNameInAndPositionIn(
        String todayDate,
        Set<String> nameSet,
        Set<String> positionSet
    );

    // 2. 기간 검색
    Flux<Attendance> findAllByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // 3. 특정 멤버의 모든 근태 기록 조회 (연관관계 대신 사용)
    Flux<Attendance> findAllByMemberId(Long memberId);
}