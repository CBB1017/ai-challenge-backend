package com.brycenkorea.template.dto.response;

import com.brycenkorea.template.contants.ActionStatus;
import com.brycenkorea.template.entity.Action;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ActionResponse(
    String id,
    String actionName,
    String content,
    ActionStatus status,
    String statusDescription,
    String roomId,
    LocalDateTime createdAt
) {
    public static ActionResponse from(Action action) {
        return ActionResponse.builder()
            .id(action.getId().toString())
            .actionName(action.getActionName())
            .content(action.getContent())
            .status(action.getStatus())
            .statusDescription(action.getStatus().getDescription())
            .roomId(action.getRoomId() != null ? action.getRoomId().toString() : null)
            .createdAt(action.getCreatedAt())
            .build();
    }
}
