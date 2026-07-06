package com.ep.databuilder.factory;

import com.ep.databuilder.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Table;

@Getter
@Setter
@Entity
@Table(name = "t_factory_param")
public class FactoryParamEntity extends BaseEntity {

    private Long factoryId;
    private String name;
    /** string/int/long/boolean/date/datetime/enum */
    private String dataType;
    /** 支持模板函数，如 test_${randomStr(8)} */
    private String defaultValue;
    private String description;
    /** 枚举JSON [{value,meaning}]，仅 enum 类型 */
    private String enums;
    private Integer sortNo = 0;
}
