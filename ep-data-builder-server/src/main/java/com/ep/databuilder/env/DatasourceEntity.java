package com.ep.databuilder.env;

import com.ep.databuilder.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Table;

@Getter
@Setter
@Entity
@Table(name = "t_datasource")
public class DatasourceEntity extends BaseEntity {

    private Long envId;
    /** 对齐猎户 schemaCode：<dbtype>.<cluster>-<schema> */
    private String schemaCode;
    /** MYSQL/DM/ORACLE/SQLITE... */
    private String dbType;
    /** HUNTER（主）/DIRECT（兜底） */
    private String channel = "HUNTER";
    private String jdbcUrl;
    private String username;
    private String passwordEnc;
}
