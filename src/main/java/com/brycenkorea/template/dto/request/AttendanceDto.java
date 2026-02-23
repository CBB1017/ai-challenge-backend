package com.brycenkorea.template.dto.request;

import com.brycenkorea.template.contants.AttendanceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceDto {

    @NotBlank
    private String memberName;

    @NotBlank
    private String position;

    @NotBlank
    private String planType;

    @NotBlank
    private String planInOut;

    @PositiveOrZero
    private float planWorkHour;

    private String actualType;

    private String actualIn;
    private String actualOut;

    private String actualWorkHour;

    private String late;
    private String exceptionWork;
    private String earlyLeave;

    @PositiveOrZero
    private float ot;

    private String vacation;
    private String approvalRequest;
    private AttendanceStatus status = AttendanceStatus.DEFAULT;
}