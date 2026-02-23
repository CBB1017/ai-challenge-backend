package com.brycenkorea.template.service;

import com.brycenkorea.template.contants.AttendanceStatus;
import com.brycenkorea.template.contants.NotificationResult;
import com.brycenkorea.template.dto.NotificationSendDto;
import com.brycenkorea.template.entity.Member;
import com.brycenkorea.template.entity.Notification;
import com.brycenkorea.template.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;


@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public boolean shouldSendAlert(Member member, AttendanceStatus status) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Optional<Notification> notification = notificationRepository.findFirstByMemberIdAndAttendanceStatusAndResultAndCreatedAtAfterOrderByCreatedAtDesc(
            member.getId(), status, NotificationResult.SUCCESS.name(), todayStart
        );
        notification.ifPresent(value -> log.info("noti => {}", value));

        // 'SUCCESS'만 조회
        return notification.isEmpty();
    }

    public void saveNotification(NotificationSendDto dto) {
        Notification notification = new Notification();
        notification.setMember(dto.member());
        notification.setAttendanceStatus(dto.status());
        notification.setMessage(dto.message());
        notification.setResult(dto.result().name());
        notification.setErrorReason(dto.errorReason());
        notification.setSlackInfo(dto.member().getSlackMemberId());
        notificationRepository.save(notification);
    }
}