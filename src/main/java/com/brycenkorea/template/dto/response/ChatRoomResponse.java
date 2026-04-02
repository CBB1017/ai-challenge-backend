package com.brycenkorea.template.dto.response;

import com.brycenkorea.template.entity.ChatRoom;

public record ChatRoomResponse(String roomId, String title, String updatedAt) {
    // Entity -> DTO 변환 팩토리 메서드
    public static ChatRoomResponse from(ChatRoom chatRoom) {
        return new ChatRoomResponse(
            chatRoom.getRoomId().toString(),
            chatRoom.getTitle(),
            chatRoom.getUpdatedAt() != null ? chatRoom.getUpdatedAt().toString() : ""
        );
    }
}