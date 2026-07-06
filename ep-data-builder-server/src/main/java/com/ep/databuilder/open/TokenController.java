package com.ep.databuilder.open;

import com.ep.databuilder.common.BizException;
import com.ep.databuilder.common.Result;
import com.ep.databuilder.log.OperationLogService;
import com.ep.databuilder.security.OpenTokenInterceptor;
import com.ep.databuilder.security.RequireRole;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/tokens")
@RequiredArgsConstructor
@RequireRole("ADMIN")
public class TokenController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OpenTokenRepository tokenRepository;
    private final OperationLogService operationLogService;

    @GetMapping
    public Result<List<TokenVO>> list() {
        return Result.ok(tokenRepository.findAll().stream().map(TokenVO::of).collect(Collectors.toList()));
    }

    /** 创建即返回明文（仅此一次），库中只存 SHA-256 */
    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody TokenCreateDTO dto) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String plaintext = "epdb_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        OpenTokenEntity entity = new OpenTokenEntity();
        entity.setName(dto.getName());
        entity.setTokenHash(OpenTokenInterceptor.sha256(plaintext));
        entity.setQpsLimit(dto.getQpsLimit() == null ? 5 : dto.getQpsLimit());
        tokenRepository.save(entity);
        operationLogService.record("TOKEN", entity.getId(), "CREATE", dto.getName());
        Map<String, Object> result = new HashMap<>();
        result.put("id", entity.getId());
        result.put("token", plaintext);
        return Result.ok(result);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody TokenUpdateDTO dto) {
        OpenTokenEntity entity = tokenRepository.findById(id)
                .orElseThrow(() -> new BizException("Token 不存在：id=" + id));
        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled());
        }
        if (dto.getQpsLimit() != null) {
            entity.setQpsLimit(dto.getQpsLimit());
        }
        tokenRepository.save(entity);
        operationLogService.record("TOKEN", id, "UPDATE",
                "enabled=" + entity.getEnabled() + " qps=" + entity.getQpsLimit());
        return Result.ok();
    }

    @Data
    public static class TokenCreateDTO {
        @NotBlank(message = "不能为空")
        private String name;
        private Integer qpsLimit;
    }

    @Data
    public static class TokenUpdateDTO {
        private Boolean enabled;
        private Integer qpsLimit;
    }

    @Data
    public static class TokenVO {
        private Long id;
        private String name;
        private Boolean enabled;
        private Integer qpsLimit;
        private LocalDateTime lastUsedAt;
        private LocalDateTime createdAt;

        static TokenVO of(OpenTokenEntity e) {
            TokenVO vo = new TokenVO();
            vo.setId(e.getId());
            vo.setName(e.getName());
            vo.setEnabled(e.getEnabled());
            vo.setQpsLimit(e.getQpsLimit());
            vo.setLastUsedAt(e.getLastUsedAt());
            vo.setCreatedAt(e.getCreatedAt());
            return vo;
        }
    }
}
