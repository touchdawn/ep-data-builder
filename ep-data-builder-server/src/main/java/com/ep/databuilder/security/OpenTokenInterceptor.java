package com.ep.databuilder.security;

import com.ep.databuilder.common.Result;
import com.ep.databuilder.open.OpenTokenEntity;
import com.ep.databuilder.open.OpenTokenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** 开放 API 的 Token 校验：Header X-Builder-Token；附带按 token 的简单秒级 QPS 限流 */
@Component
@RequiredArgsConstructor
public class OpenTokenInterceptor implements HandlerInterceptor {

    public static final String TOKEN_HEADER = "X-Builder-Token";
    public static final String ATTR_TOKEN_NAME = "openTokenName";

    private final OpenTokenRepository openTokenRepository;
    private final ObjectMapper objectMapper;

    /** tokenHash -> [秒窗口起点, 窗口内计数] */
    private final Map<String, long[]> windows = new ConcurrentHashMap<>();
    private final AtomicInteger cleanupCounter = new AtomicInteger();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String token = request.getHeader(TOKEN_HEADER);
        if (token == null || token.isEmpty()) {
            return reject(response, 401, "缺少 " + TOKEN_HEADER + " 请求头");
        }
        String hash = sha256(token);
        Optional<OpenTokenEntity> found = openTokenRepository.findByTokenHashAndEnabledTrue(hash);
        if (!found.isPresent()) {
            return reject(response, 401, "无效的开放 API Token");
        }
        OpenTokenEntity entity = found.get();
        if (!allow(hash, entity.getQpsLimit() == null ? 5 : entity.getQpsLimit())) {
            return reject(response, 429, "超出 QPS 配额（" + entity.getQpsLimit() + "/s），请降低调用频率");
        }
        entity.setLastUsedAt(LocalDateTime.now());
        openTokenRepository.save(entity);
        request.setAttribute(ATTR_TOKEN_NAME, entity.getName());
        return true;
    }

    private synchronized boolean allow(String hash, int qps) {
        long nowSec = System.currentTimeMillis() / 1000;
        long[] window = windows.get(hash);
        if (window == null || window[0] != nowSec) {
            windows.put(hash, new long[]{nowSec, 1});
            if (cleanupCounter.incrementAndGet() % 1000 == 0) {
                windows.entrySet().removeIf(e -> e.getValue()[0] < nowSec - 60);
            }
            return true;
        }
        if (window[1] >= qps) {
            return false;
        }
        window[1]++;
        return true;
    }

    public static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(status, message)));
        return false;
    }
}
