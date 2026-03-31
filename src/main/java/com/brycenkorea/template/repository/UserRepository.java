package com.brycenkorea.template.repository;

import com.brycenkorea.template.entity.User;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserRepository extends ReactiveCrudRepository<User, String> {
    Mono<User> findByEmail(String email);

    Flux<User> findByRole(String role);
    Flux<User> findByDepartment(String department);
    Flux<User> findByName(String name);
    Flux<User> findByPosition(String position);
}