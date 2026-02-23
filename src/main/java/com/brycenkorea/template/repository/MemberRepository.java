package com.brycenkorea.template.repository;

import com.brycenkorea.template.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByName(String name);

    Optional<Member> findByEmail(String email);

    List<Member> findAllByNameInAndPositionIn(Set<String> nameSet, Set<String> positionSet);

    @Query("""
    SELECT m FROM Member m
    WHERE LOWER(m.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
       OR LOWER(m.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
       OR LOWER(m.position) LIKE LOWER(CONCAT('%', :keyword, '%'))
       OR LOWER(m.slackMemberId) LIKE LOWER(CONCAT('%', :keyword, '%'))
""")
    Page<Member> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}