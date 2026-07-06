package com.ep.databuilder.buildrec;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** 步骤执行轨迹（渲染后内容已脱敏） */
@Getter
@Setter
@Entity
@Table(name = "t_build_step_log")
public class BuildStepLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long buildId;
    private Integer sortNo;
    private String stepType;
    private String stepName;
    private Boolean skipped = Boolean.FALSE;
    /** 渲染后的报文/SQL */
    private String request;
    /** 响应摘要/影响行数 */
    private String response;
    private String outputsJson;
    /** SUCCESS/FAILED/SKIPPED */
    private String status;
    private String errorMsg;
    private Long durationMs;

    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
