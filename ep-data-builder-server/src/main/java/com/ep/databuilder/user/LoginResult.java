package com.ep.databuilder.user;

import lombok.Data;

@Data
public class LoginResult {

    private String token;
    private String username;
    private String displayName;
    private String role;
}
