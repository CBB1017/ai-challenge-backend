package com.brycenkorea.template.repository;

import com.brycenkorea.template.entity.Member;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

public interface MemberRepository extends ReactiveCrudRepository<Member, Long> {
    Mono<Member> findByName(String name);

    Mono<Member> findByEmail(String email);

    // List<Member> -> Flux<Member>로 변경
    @Query("""
        SELECT * FROM member
        WHERE name IN (:names)
          AND position IN (:positions)
    """)
    Flux<Member> findAllByNameInAndPositionIn(Set<String> nameSet, Set<String> positionSet);

    @Query("""
        SELECT * FROM member
        WHERE name LIKE CONCAT('%', :keyword, '%')
           OR email LIKE CONCAT('%', :keyword, '%')
        ORDER BY id DESC
        LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}
    """)
    Flux<Member> searchByKeyword(String keyword, Pageable pageable);

    Flux<Member> findAllBy(Pageable pageable);
}