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
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;


@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;

    /**
     * 알림을 보내야 하는지 여부를 판단 (비동기 반환)
     * 결과가 없으면(Empty) 알림을 보내야 하므로 true 반환
     */
    public Mono<Boolean> shouldSendAlert(Member member, AttendanceStatus status) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        return notificationRepository.findLatestNotification(
                member.getId(),
                status,
                NotificationResult.SUCCESS.name(),
                todayStart
            )
            .doOnNext(value -> log.info("기존 알림 존재 => {}", value))
            .map(value -> false)          // 데이터가 있으면 이미 보낸 것이므로 false
            .defaultIfEmpty(true);        // 데이터가 없으면 새로 보내야 하므로 true
    }

    /**
     * 알림 이력 저장
     */
    public Mono<Notification> saveNotification(NotificationSendDto dto) {
        Notification notification = new Notification();
        // member 객체 전체가 아닌 ID만 세팅 (R2DBC 리팩토링 반영)
        notification.setMemberId(dto.member().getId());
        notification.setAttendanceStatus(dto.status());
        notification.setMessage(dto.message());
        notification.setResult(dto.result().name());
        notification.setErrorReason(dto.errorReason());
        notification.setSlackInfo(dto.member().getSlackMemberId());

        return notificationRepository.save(notification)
            .doOnSuccess(saved -> log.info("알림 이력 저장 완료: {}", saved.getId()));
    }
}