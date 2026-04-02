package com.brycenkorea.template.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.*;
import org.springframework.data.relational.core.mapping.Column;

import java.time.LocalDateTime;

@Getter
public abstract class BaseEntity {

    @Id
    @Setter
    private Long id;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column("created_by")
    private Long createdBy;

    @LastModifiedBy
    @Column("updated_by")
    private Long updatedBy;
}