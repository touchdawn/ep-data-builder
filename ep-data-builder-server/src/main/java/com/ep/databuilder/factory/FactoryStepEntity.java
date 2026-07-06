package com.ep.databuilder.factory;

import com.ep.databuilder.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Table;

@Getter
@Setter
@Entity
@Table(name = "t_factory_step")
public class FactoryStepEntity extends BaseEntity {

    private Long factoryId;
    private Integer sortNo;
    /** API_CALL/SQL_EXEC（REF/CHECK 为 M2） */
    private String stepType;
    private String name;
    /** 条件表达式，空=总是执行 */
    private String conditionExpr;
    /** 类型专属配置JSON */
    private String config;
    /** 输出提取JSON [{var, expr}] */
    private String outputs;
}
