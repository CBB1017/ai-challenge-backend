package com.brycenkorea.template.repository;

import com.brycenkorea.template.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    List<Attendance> findAllByAttendanceDtAndMemberNameInAndMemberPositionIn(
        String todayDate,
        Set<String> nameSet,
        Set<String> positionSet
    );

    List<Attendance> findAllByCreatedAtBetween(LocalDateTime createdAtAfter, LocalDateTime createdAtBefore);
}