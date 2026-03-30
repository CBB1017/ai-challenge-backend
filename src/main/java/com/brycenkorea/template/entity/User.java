package com.brycenkorea.template.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    private String email;
    private String name;        // "문병찬"
    private String position;    // "대리"
    private String department;  // "DX 2Team"
    private String corpCode;    // "T06071"
    private String role;
    private OffsetDateTime lastLoginAt;
    private OffsetDateTime createdAt;

    // 신규 유저 여부를 판단하기 위한 유틸리티
    public static User fromGroupware(String email, String name, String position, String department) {
        return User.builder().email(email).name(name).position(position).department(department).role("ROLE_USER").build();
    }
}
