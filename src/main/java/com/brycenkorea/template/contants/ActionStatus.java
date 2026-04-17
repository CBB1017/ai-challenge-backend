package com.brycenkorea.template.contants;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ActionStatus {
    PENDING("대기"),
    SUCCESS("성공"),
    ERROR("에러"),
    ROLLBACK("롤백");

    private final String description;
}
