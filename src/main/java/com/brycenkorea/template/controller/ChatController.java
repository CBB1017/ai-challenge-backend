package com.brycenkorea.template.controller;

import com.brycenkorea.template.dto.ChatMessageResponse;
import com.brycenkorea.template.dto.ChatRoomResponse;
import com.brycenkorea.template.dto.response.PromptRequest;
import com.brycenkorea.template.dto.response.PromptResponse;
import com.brycenkorea.template.entity.ChatRoom;
import com.brycenkorea.template.repository.ChatMessageRepository;
import com.brycenkorea.template.repository.ChatRoomRepository;
import com.brycenkorea.template.service.GeminiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;

    @GetMapping("/rooms")
    public Flux<ChatRoomResponse> getChatRooms(Authentication authentication) {
        String userId = authentication.getName();

        return chatRoomRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                                 .map(ChatRoomResponse::from);
    }

    @PostMapping("/room")
    public Mono<ChatRoomResponse> createChatRoom(Authentication authentication) {
        String userId = authentication.getName();

        ChatRoom newRoom = ChatRoom.builder()
                                   .userId(userId)
                                   .title("새로운 대화")
                                   .build();

        return chatRoomRepository.save(newRoom).map(ChatRoomResponse::from);
    }

    // 특정 채팅방의 모든 메시지를 과거순으로 조회
    @GetMapping("/rooms/{roomId}/messages")
    public Flux<ChatMessageResponse> getMessages(@PathVariable UUID roomId) {
        // V7 인덱스(idx_chat_message_room_created) 덕분에 매우 빠릅니다.
        return chatMessageRepository.findByRoomIdOrderByCreatedAtAsc(roomId)
                                    .map(msg -> new ChatMessageResponse(
                                        msg.getRole(),
                                        msg.getContent(),
                                        msg.getCreatedAt()
                                    ));
    }
}