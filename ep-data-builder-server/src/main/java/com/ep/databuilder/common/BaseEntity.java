package com.ep.databuilder.common;

import com.ep.databuilder.security.UserContext;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import java.time.LocalDateTime;

/** 统一主键 + 审计四列。IDENTITY 主键在 SQLite/MySQL/达梦上均原生支持 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        createdBy = UserContext.usernameOrSystem();
        updatedBy = createdBy;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
        updatedBy = UserContext.usernameOrSystem();
    }
}
