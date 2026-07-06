package com.ep.databuilder.buildrec;

import com.ep.databuilder.security.UserContext;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** 一次造数执行。只增不改状态外字段，不继承 BaseEntity（无 updated 列） */
@Getter
@Setter
@Entity
@Table(name = "t_build")
public class BuildEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long factoryId;
    private Long envId;
    /** CONSOLE/OPEN_API/SEED/TRIAL/REF */
    private String source;
    private Long parentBuildId;
    /** OPEN_API 来源时记录消费方 */
    private String tokenName;
    private Long recipeId;
    /** 合并后的最终参数（默认值 ← 套餐 ← 调用方） */
    private String paramsJson;
    /** RUNNING/SUCCESS/FAILED */
    private String status;
    private String outputsJson;
    private String errorMsg;
    private Long durationMs;

    private LocalDateTime createdAt;
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
            createdBy = UserContext.usernameOrSystem();
        }
    }
}
