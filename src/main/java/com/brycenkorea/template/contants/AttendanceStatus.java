package com.brycenkorea.template.contants;

import lombok.Getter;

/**
 * _NOTIFICATION suffix가 slack message push 기준
 * Enumeration representing various attendance statuses for an individual.
 * Each status describes a specific state related to attendance or work.
 */
@Getter
public enum AttendanceStatus {
    NOT_CHECKED_IN("출근 전입니다."), ON_LEAVE("휴가 중입니다."), IDLE("대기 상태입니다."), DEFAULT("어느 상태에도 해당하지 않는 기본 상태입니다."), WORKING(
        "근무 중입니다."), WORK_TIME_OVER("근무 시간 이후입니다."), OVERTIME_WORKING("초과 근무 중입니다."), CHECKED_OUT("퇴근 처리되었습니다."), CHECKED_OUT_OT(
        "초과근무 후 퇴근입니다."), NEED_BREAK_NOTIFICATION("퇴근체크 확인해 주세요."), NEED_OVERTIME_NOTIFICATION("출퇴근 데이터 기반 초과근무 알림입니다."), NOT_CHECKED_IN_NOTIFICATION(
        "출근체크 확인해 주세요.");

    private final String message;

    AttendanceStatus(String message) {
        this.message = message;
    }

}