package com.brycenkorea.template.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements Persistable<String> {

    @Id
    private String email;
    private String name;        // "문병찬"
    private String position;    // "대리"
    private String department;  // "DX 2Team"
    private String role;
    @Column("last_login_at")
    private OffsetDateTime lastLoginAt;
    @Column("created_at")
    private OffsetDateTime createdAt;

    // R2DBC에서 String ID를 쓸 때 save()가 insert가 아닌 update로 동작하는 것을 방지
    @Transient
    private boolean isNew = false;

    // 신규 유저 여부를 판단하기 위한 유틸리티
    public static User fromGroupware(String email, String name, String position, String department) {
        return User.builder().email(email).name(name).position(position).department(department).role("ROLE_USER").build();
    }

    @Override
    public String getId() { return email; }

    @Override
    public boolean isNew() { return isNew || createdAt == null; }
}
