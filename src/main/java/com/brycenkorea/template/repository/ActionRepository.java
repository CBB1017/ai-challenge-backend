package com.brycenkorea.template.repository;

import com.brycenkorea.template.entity.Action;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
public interface ActionRepository extends R2dbcRepository<Action, UUID> {
    Flux<Action> findAllByUserIdOrderByCreatedAtDesc(String userId);

    Flux<Action> findAllByRoomIdOrderByCreatedAtDesc(UUID roomId);
}
