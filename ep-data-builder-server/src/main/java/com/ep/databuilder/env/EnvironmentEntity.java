package com.ep.databuilder.env;

import com.ep.databuilder.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Table;

@Getter
@Setter
@Entity
@Table(name = "t_environment")
public class EnvironmentEntity extends BaseEntity {

    private String code;
    private String name;
    private String description;
}
