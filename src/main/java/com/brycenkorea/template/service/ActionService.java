package com.brycenkorea.template.service;

import com.brycenkorea.template.contants.ActionStatus;
import com.brycenkorea.template.entity.Action;
import com.brycenkorea.template.repository.ActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActionService {

    private final ActionRepository actionRepository;

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
}
