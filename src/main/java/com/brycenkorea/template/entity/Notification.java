package com.brycenkorea.template.entity;

import com.brycenkorea.template.contants.AttendanceStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("notification") // @Entity -> @Table
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class Notification extends BaseEntity {

    @Column("member_id") // 객체 참조 대신 ID 직접 저장
    private Long memberId;

    @Transient // 조인 결과를 담기 위한 용도 (DB 저장 제외)
    @ToString.Exclude
    private Member member;

    @Column("slack_info")
    private String slackInfo;

    // R2DBC는 기본적으로 Enum을 String으로 매핑하려고 시도합니다.
    // DB 컬럼 타입이 VARCHAR라면 그대로 사용 가능합니다.
    @Column("attendance_status")
    private AttendanceStatus attendanceStatus;

    @Column("message")
    private String message;

    @Column("result")
    private String result;

    @Column("error_reason")
    private String errorReason;
}