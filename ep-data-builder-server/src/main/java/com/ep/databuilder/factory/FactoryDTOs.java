package com.ep.databuilder.factory;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class FactoryDTOs {

    @Data
    public static class FactoryCreateDTO {
        @NotBlank(message = "不能为空")
        private String code;
        @NotBlank(message = "不能为空")
        private String name;
        @NotBlank(message = "不能为空：功能描述是 LLM 造数推理的检索依据")
        private String description;
        private String owner;
    }

    @Data
    public static class FactorySaveDTO {
        @NotBlank(message = "不能为空")
        private String name;
        @NotBlank(message = "不能为空：功能描述是 LLM 造数推理的检索依据")
        private String description;
        private String owner;
        private Boolean enabled = Boolean.TRUE;
        private Long lockVersion;
        private List<ParamDTO> params = new ArrayList<>();
        private List<StepDTO> steps = new ArrayList<>();
    }

    @Data
    public static class ParamDTO {
        private String name;
        private String dataType;
        private String defaultValue;
        private String description;
        private List<EnumItemDTO> enums = new ArrayList<>();
        private Integer sortNo;
    }

    @Data
    public static class EnumItemDTO {
        private String value;
        private String meaning;
    }

    @Data
    public static class StepDTO {
        private Integer sortNo;
        private String stepType;
        private String name;
        private String conditionExpr;
        /** 类型专属配置，前端原样透传，后端按类型校验 */
        private JsonNode config;
        private List<OutputDTO> outputs = new ArrayList<>();
    }

    @Data
    public static class OutputDTO {
        private String var;
        private String expr;
    }

    @Data
    public static class FactoryListVO {
        private Long id;
        private String code;
        private String name;
        private String description;
        private String owner;
        private Boolean enabled;
        private Boolean pureSql;
        private LocalDateTime updatedAt;

        public static FactoryListVO of(FactoryEntity e) {
            FactoryListVO vo = new FactoryListVO();
            vo.setId(e.getId());
            vo.setCode(e.getCode());
            vo.setName(e.getName());
            vo.setDescription(e.getDescription());
            vo.setOwner(e.getOwner());
            vo.setEnabled(e.getEnabled());
            vo.setPureSql(e.getPureSql());
            vo.setUpdatedAt(e.getUpdatedAt());
            return vo;
        }
    }

    @Data
    public static class FactoryDetailDTO {
        private Long id;
        private String code;
        private String name;
        private String description;
        private String owner;
        private Boolean enabled;
        private Boolean pureSql;
        private Long lockVersion;
        private List<ParamDTO> params = new ArrayList<>();
        private List<StepDTO> steps = new ArrayList<>();
    }
}
