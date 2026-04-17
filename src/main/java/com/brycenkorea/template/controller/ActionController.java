package com.brycenkorea.template.controller;

import com.brycenkorea.template.dto.response.ActionResponse;
import com.brycenkorea.template.repository.ActionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/actions")
@RequiredArgsConstructor
@Tag(name = "Action", description = "액션 로그 관련 API")
public class ActionController {

    private final ActionRepository actionRepository;

    @GetMapping("/my")
    @Operation(summary = "내 액션 리스트 조회", description = "최근 수행한 액션 목록을 조회합니다.")
    public Flux<ActionResponse> getMyActions(Authentication authentication) {
        String userId = Objects.requireNonNull(authentication.getPrincipal()).toString();

        return actionRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
            .map(ActionResponse::from);
    }

    @GetMapping("/room/{roomId}")
    @Operation(summary = "채팅방별 액션 리스트 조회", description = "특정 채팅방에서 발생한 액션 목록을 조회합니다.")
    public Flux<ActionResponse> getActionsByRoom(@PathVariable UUID roomId) {
        return actionRepository.findAllByRoomIdOrderByCreatedAtDesc(roomId)
            .map(ActionResponse::from);
    }
}
