package com.brycenkorea.template.service;

import com.brycenkorea.template.contants.ActionStatus;
import com.brycenkorea.template.entity.Action;
import com.brycenkorea.template.repository.ActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActionService {

    private final ActionRepository actionRepository;
    private final SseBroadcaster sseBroadcaster;

    public Mono<Action> logAction(String actionName, String content, ActionStatus status, String userId, UUID roomId) {
        Action action = Action.builder()
            .actionName(actionName)
            .content(content)
            .status(status)
            .userId(userId)
            .roomId(roomId)
            .build();
        return actionRepository.save(action)
            .doOnSuccess(a -> log.info("[Action] Logged: {} ({}) for {}", actionName, status, userId))
            .doOnError(e -> log.error("[Action] Logging failed: {}", e.getMessage()));
    }

    /**
     * 액션을 로그에 남기고 즉시 브로드캐스트합니다.
     */
    public Mono<Action> logActionAndBroadcast(String actionName, String content, ActionStatus status, String userId, UUID roomId) {
        return logAction(actionName, content, status, userId, roomId)
            .flatMap(savedAction -> broadcastActionList(roomId, userId).thenReturn(savedAction));
    }

    /**
     * 특정 방의 액션 상태를 업데이트하고 결과를 브로드캐스트합니다.
     */
    public Mono<Action> updateStatusAndBroadcast(UUID roomId, String userId, String actionName, ActionStatus newStatus) {
        return actionRepository.findAllByRoomIdOrderByCreatedAtDesc(roomId)
            .filter(a -> actionName.equals(a.getActionName()) && a.getStatus() == ActionStatus.IN_PROGRESS)
            .next()
            .flatMap(action -> {
                action.setStatus(newStatus);
                return actionRepository.save(action);
            })
            .flatMap(savedAction -> broadcastActionList(roomId, userId).thenReturn(savedAction))
            .doOnSuccess(a -> log.info("[Action] Status updated to {} for action {}", newStatus, actionName));
    }

    /**
     * 특정 방의 모든 액션 목록을 조회하여 SSE로 브로드캐스트합니다.
     */
    public Mono<Void> broadcastActionList(UUID roomId, String userId) {
        return actionRepository.findAllByRoomIdOrderByCreatedAtDesc(roomId)
            .map(com.brycenkorea.template.dto.response.ActionResponse::from)
            .collectList()
            .doOnNext(actions -> sseBroadcaster.sendEvent(userId, "action-list-update", actions))
            .then();
    }
}
