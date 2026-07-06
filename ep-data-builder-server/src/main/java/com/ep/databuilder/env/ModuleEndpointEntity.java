package com.ep.databuilder.env;

import com.ep.databuilder.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Table;

@Getter
@Setter
@Entity
@Table(name = "t_module_endpoint")
public class ModuleEndpointEntity extends BaseEntity {

    private Long envId;
    private String moduleCode;
    private String baseUrl;
    /** 公共 Header JSON，如网关鉴权头 */
    private String headers;
}
