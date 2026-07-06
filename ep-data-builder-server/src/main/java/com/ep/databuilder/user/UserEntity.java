package com.ep.databuilder.user;

import com.ep.databuilder.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Table;

@Getter
@Setter
@Entity
@Table(name = "t_user")
public class UserEntity extends BaseEntity {

    private String username;
    private String passwordHash;
    private String displayName;
    private String role = "EDITOR";
    private Boolean enabled = Boolean.TRUE;
}
