package com.ep.databuilder.user;

import com.ep.databuilder.common.BizException;
import com.ep.databuilder.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public LoginResult login(String username, String password) {
        UserEntity user = userRepository.findByUsernameAndEnabledTrue(username)
                .orElseThrow(() -> new BizException("用户名或密码错误"));
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw new BizException("用户名或密码错误");
        }
        LoginResult result = new LoginResult();
        result.setToken(jwtUtil.generate(user));
        result.setUsername(user.getUsername());
        result.setDisplayName(user.getDisplayName());
        result.setRole(user.getRole());
        return result;
    }
}
