package com.ep.databuilder.open;

import com.ep.databuilder.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "t_open_token")
public class OpenTokenEntity extends BaseEntity {

    /** 消费方名称，如 test-gen-pipeline */
    private String name;
    /** SHA-256，明文只在创建时展示一次 */
    private String tokenHash;
    private Boolean enabled = Boolean.TRUE;
    private Integer qpsLimit = 5;
    private LocalDateTime lastUsedAt;
}
