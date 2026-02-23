package com.brycenkorea.template.repository;

import com.brycenkorea.template.contants.AttendanceStatus;
import com.brycenkorea.template.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    // 최근 1건 (member + attendanceStatus 조합, 30분 이내)
    Optional<Notification> findFirstByMemberIdAndAttendanceStatusAndResultAndCreatedAtAfterOrderByCreatedAtDesc(
        Long memberId, AttendanceStatus attendanceStatus, String result, LocalDateTime after);
}