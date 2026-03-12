package com.brycenkorea.template.entity;

import lombok.*;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("attendance") // @Entity 대신 @Table
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Attendance extends BaseEntity {

    // JPA의 @ManyToOne 대신 외래키 ID를 직접 가집니다.
    @Column("member_id")
    private Long memberId;

    // 필요 시 조회 후 채워넣기 위한 비즈니스 로직용 필드 (DB 저장 안됨)
    @Transient
    private Member member;

    @Column("attendance_dt")
    private String attendanceDt;

    @Column("member_name")
    private String memberName;

    @Column("position")
    private String position;

    @Column("plan_type")
    private String planType;

    @Column("plan_in_out")
    private String planInOut;

    @Column("plan_work_hour")
    private Float planWorkHour;

    @Column("actual_type")
    private String actualType;

    @Column("actual_in")
    private String actualIn;

    @Column("actual_out")
    private String actualOut;

    @Column("actual_work_hour")
    private String actualWorkHour;

    @Column("late")
    private String late;

    @Column("exception_work")
    private String exceptionWork;

    @Column("early_leave")
    private String earlyLeave;

    @Column("ot")
    private Float ot;

    @Column("vacation")
    private String vacation;

    @Column("approval_request")
    private String approvalRequest;
}