package com.brycenkorea.template.dto;

import com.brycenkorea.template.contants.AttendanceStatus;
import com.brycenkorea.template.contants.NotificationResult;
import com.brycenkorea.template.entity.Member;

public record NotificationSendDto(Member member,
                                  AttendanceStatus status,
                                  String message,
                                  NotificationResult result,
                                  String errorReason) {}