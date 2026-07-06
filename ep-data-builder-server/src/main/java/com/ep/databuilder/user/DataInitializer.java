package com.ep.databuilder.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }
        UserEntity admin = new UserEntity();
        admin.setUsername("admin");
        admin.setPasswordHash(new BCryptPasswordEncoder().encode("admin123"));
        admin.setDisplayName("管理员");
        admin.setRole("ADMIN");
        admin.setEnabled(true);
        userRepository.save(admin);
        log.warn("已初始化默认管理员 admin / admin123，请登录后尽快修改密码");
    }
}
