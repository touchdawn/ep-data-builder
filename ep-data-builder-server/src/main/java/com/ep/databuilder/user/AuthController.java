package com.ep.databuilder.user;

import com.ep.databuilder.common.Result;
import com.ep.databuilder.security.LoginUser;
import com.ep.databuilder.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    public Result<LoginResult> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(userService.login(request.getUsername(), request.getPassword()));
    }

    @GetMapping("/me")
    public Result<LoginUser> me() {
        return Result.ok(UserContext.get());
    }
}
