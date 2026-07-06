package com.ep.databuilder.buildrec;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BuildDTOs {

    @Data
    public static class ConsoleBuildRequest {
        @NotNull(message = "不能为空")
        private Long factoryId;
        @NotNull(message = "不能为空")
        private Long envId;
        private Map<String, Object> params;
    }

    @Data
    public static class BuildListVO {
        private Long id;
        private String factoryCode;
        private String factoryName;
        private String envCode;
        private String source;
        private String tokenName;
        private String status;
        private String errorMsg;
        private Long durationMs;
        private String createdBy;
        private LocalDateTime createdAt;
    }

    @Data
    public static class BuildDetailVO {
        private Long id;
        private String factoryCode;
        private String factoryName;
        private String envCode;
        private String source;
        private String tokenName;
        private String status;
        private String paramsJson;
        private String outputsJson;
        private String errorMsg;
        private Long durationMs;
        private String createdBy;
        private LocalDateTime createdAt;
        private List<StepLogVO> steps = new ArrayList<>();
    }

    @Data
    public static class StepLogVO {
        private Integer sortNo;
        private String stepType;
        private String stepName;
        private Boolean skipped;
        private String request;
        private String response;
        private String outputsJson;
        private String status;
        private String errorMsg;
        private Long durationMs;
    }
}
