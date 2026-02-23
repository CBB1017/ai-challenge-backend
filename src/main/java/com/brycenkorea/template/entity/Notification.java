package com.brycenkorea.template.entity;

import com.brycenkorea.template.contants.AttendanceStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    @ToString.Exclude
    private Member member;

    @Column(name = "slack_info", length = 300)
    private String slackInfo;  // Slack DM Webhook URL(또는 slackMemberId도 가능)

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", length = 30, nullable = false)
    private AttendanceStatus attendanceStatus;      // 알림 타입

    @Column(name = "message", length = 2000, nullable = false)
    private String message;        // 실제 전송 메시지(알림 내용)

    @Column(name = "result", length = 20, nullable = false)
    private String result;         // 결과("SUCCESS", "FAIL", "PENDING" 등)

    @Column(name = "error_reason", length = 500)
    private String errorReason;    // 실패 원인(선택, 에러 메시지 등)

}