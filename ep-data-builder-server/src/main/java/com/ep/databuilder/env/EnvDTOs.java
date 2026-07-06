package com.ep.databuilder.env;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 环境域的请求/响应对象（小对象集中放，避免文件碎片化） */
public class EnvDTOs {

    @Data
    public static class EnvSaveDTO {
        @NotBlank(message = "不能为空")
        private String code;
        @NotBlank(message = "不能为空")
        private String name;
        private String description;
    }

    @Data
    public static class EndpointSaveDTO {
        @NotBlank(message = "不能为空")
        private String moduleCode;
        @NotBlank(message = "不能为空")
        private String baseUrl;
        /** 公共 Header JSON 文本 */
        private String headers;
    }

    @Data
    public static class DatasourceSaveDTO {
        @NotBlank(message = "不能为空")
        private String schemaCode;
        @NotBlank(message = "不能为空")
        private String dbType;
        @NotBlank(message = "不能为空")
        private String channel;
        private String jdbcUrl;
        private String username;
        /** 明文密码，仅入参；编辑时留空 = 不修改 */
        private String password;
    }

    @Data
    public static class DatasourceVO {
        private Long id;
        private String schemaCode;
        private String dbType;
        private String channel;
        private String jdbcUrl;
        private String username;

        public static DatasourceVO of(DatasourceEntity e) {
            DatasourceVO vo = new DatasourceVO();
            vo.setId(e.getId());
            vo.setSchemaCode(e.getSchemaCode());
            vo.setDbType(e.getDbType());
            vo.setChannel(e.getChannel());
            vo.setJdbcUrl(e.getJdbcUrl());
            vo.setUsername(e.getUsername());
            return vo;
        }
    }
}
