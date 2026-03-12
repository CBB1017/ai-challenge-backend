package com.brycenkorea.template.repository;

import com.brycenkorea.template.contants.AttendanceStatus;
import com.brycenkorea.template.entity.Notification;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface NotificationRepository extends ReactiveCrudRepository<Notification, Long> {

    /**
     * 특정 회원, 상태, 결과에 대해 지정된 시간 이후의 최신 알림 1건 조회
     */
    @Query("""
        SELECT * FROM notification 
        WHERE member_id = :memberId 
          AND attendance_status = :attendanceStatus 
          AND result = :result 
          AND created_at > :after 
        ORDER BY created_at DESC 
        LIMIT 1
    """)
    Mono<Notification> findLatestNotification(
        Long memberId,
        AttendanceStatus attendanceStatus,
        String result,
        LocalDateTime after
    );
}