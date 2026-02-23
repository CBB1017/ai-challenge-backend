package com.brycenkorea.template.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"attendance_dt", "member_id"})
    }
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(callSuper = false)
public class Attendance  extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    @JsonBackReference
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Member member;

    @Column(name = "attendance_dt", length = 10, nullable = false)
    private String attendanceDt;     // 날짜

    @Column(name = "member_name", length = 20)
    private String memberName;         // 성명

    @Column(name = "position", length = 20)
    private String position;         // 직급

    @Column(name = "plan_type", length = 20)
    private String planType;         // 계획 근무 유형

    @Column(name = "plan_in_out", length = 20)
    private String planInOut;        // 계획 출퇴근 시간 (ex: 08:30~17:30)

    @Column(name = "plan_work_hour")
    private Float planWorkHour;      // 계획 근무시간

    @Column(name = "actual_type", length = 20)
    private String actualType;       // 실제 근무 유형

    @Column(name = "actual_in", length = 5)
    private String actualIn;         // 실제 출근 시간

    @Column(name = "actual_out", length = 5)
    private String actualOut;        // 실제 퇴근 시간

    @Column(name = "actual_work_hour")
    private String actualWorkHour;    // 실제 근무시간

    @Column(name = "late", length = 10)
    private String late;             // 지각 여부 ("" or 값)

    @Column(name = "exception_work", length = 20)
    private String exceptionWork;    // 예외 근무

    @Column(name = "early_leave", length = 10)
    private String earlyLeave;       // 조퇴 여부

    @Column(name = "ot")
    private Float ot;                // 연장근무 시간

    @Column(name = "vacation", length = 20)
    private String vacation;         // 휴가

    @Column(name = "approval_request", length = 20)
    private String approvalRequest;  // 승인요청
}