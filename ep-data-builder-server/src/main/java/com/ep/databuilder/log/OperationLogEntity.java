package com.ep.databuilder.log;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** 操作留痕，只增不改，不继承 BaseEntity（无审计四列） */
@Getter
@Setter
@Entity
@Table(name = "t_operation_log")
public class OperationLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FACTORY / ENV / DATASOURCE / TOKEN / USER */
    private String bizType;
    private Long bizId;
    /** CREATE / UPDATE / DELETE / ENDPOINT_CREATE / ENDPOINT_UPDATE / ENDPOINT_DELETE ... */
    private String action;
    private String detail;
    private String operator;
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
