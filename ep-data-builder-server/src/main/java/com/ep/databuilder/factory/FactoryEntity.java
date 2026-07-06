package com.ep.databuilder.factory;

import com.ep.databuilder.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Version;

@Getter
@Setter
@Entity
@Table(name = "t_factory")
public class FactoryEntity extends BaseEntity {

    /** {moduleCode}.{实体名}，全局唯一 */
    private String code;
    private String name;
    /** 功能描述：LLM 造数推理的检索依据，保存时非空校验 */
    private String description;
    private String owner;
    private Boolean enabled = Boolean.TRUE;
    /** 纯 SQL 工厂标记（无 API 步骤，承担表结构漂移风险），保存时自动计算 */
    private Boolean pureSql = Boolean.FALSE;

    @Version
    private Long lockVersion;

    private Boolean deleted = Boolean.FALSE;
}
