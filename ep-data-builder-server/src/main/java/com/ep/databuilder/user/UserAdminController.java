package com.ep.databuilder.user;

import com.ep.databuilder.common.BizException;
import com.ep.databuilder.common.Result;
import com.ep.databuilder.log.OperationLogService;
import com.ep.databuilder.security.RequireRole;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@RequireRole("ADMIN")
public class UserAdminController {

    private final UserRepository userRepository;
    private final OperationLogService operationLogService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @GetMapping
    public Result<List<UserVO>> list() {
        return Result.ok(userRepository.findAll().stream().map(UserVO::of).collect(Collectors.toList()));
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody UserCreateDTO dto) {
        userRepository.findByUsernameAndEnabledTrue(dto.getUsername()).ifPresent(u -> {
            throw new BizException("用户名已存在：" + dto.getUsername());
        });
        UserEntity user = new UserEntity();
        user.setUsername(dto.getUsername());
        user.setPasswordHash(encoder.encode(dto.getPassword()));
        user.setDisplayName(dto.getDisplayName());
        user.setRole(validRole(dto.getRole()));
        user.setEnabled(true);
        userRepository.save(user);
        operationLogService.record("USER", user.getId(), "CREATE", dto.getUsername());
        return Result.ok(user.getId());
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody UserUpdateDTO dto) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new BizException("用户不存在：id=" + id));
        if (dto.getDisplayName() != null) {
            user.setDisplayName(dto.getDisplayName());
        }
        if (dto.getRole() != null) {
            user.setRole(validRole(dto.getRole()));
        }
        if (dto.getEnabled() != null) {
            user.setEnabled(dto.getEnabled());
        }
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            user.setPasswordHash(encoder.encode(dto.getPassword()));
        }
        userRepository.save(user);
        operationLogService.record("USER", id, "UPDATE", user.getUsername());
        return Result.ok();
    }

    private static String validRole(String role) {
        if (!"ADMIN".equals(role) && !"EDITOR".equals(role) && !"VIEWER".equals(role)) {
            throw new BizException("角色只能是 ADMIN/EDITOR/VIEWER");
        }
        return role;
    }

    @Data
    public static class UserCreateDTO {
        @NotBlank(message = "不能为空")
        private String username;
        @NotBlank(message = "不能为空")
        private String password;
        private String displayName;
        @NotBlank(message = "不能为空")
        private String role;
    }

    @Data
    public static class UserUpdateDTO {
        private String displayName;
        private String role;
        private Boolean enabled;
        private String password;
    }

    @Data
    public static class UserVO {
        private Long id;
        private String username;
        private String displayName;
        private String role;
        private Boolean enabled;
        private LocalDateTime createdAt;

        static UserVO of(UserEntity e) {
            UserVO vo = new UserVO();
            vo.setId(e.getId());
            vo.setUsername(e.getUsername());
            vo.setDisplayName(e.getDisplayName());
            vo.setRole(e.getRole());
            vo.setEnabled(e.getEnabled());
            vo.setCreatedAt(e.getCreatedAt());
            return vo;
        }
    }
}
