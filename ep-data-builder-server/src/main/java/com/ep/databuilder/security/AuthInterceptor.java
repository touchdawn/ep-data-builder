package com.ep.databuilder.security;

import com.ep.databuilder.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final Map<String, Integer> ROLE_RANK = new HashMap<>();

    static {
        ROLE_RANK.put("VIEWER", 1);
        ROLE_RANK.put("EDITOR", 2);
        ROLE_RANK.put("ADMIN", 3);
    }

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return reject(response, 401, "未登录");
        }
        try {
            UserContext.set(jwtUtil.parse(auth.substring(7)));
        } catch (Exception e) {
            return reject(response, 401, "登录已过期，请重新登录");
        }

        HandlerMethod method = (HandlerMethod) handler;
        RequireRole require = method.getMethodAnnotation(RequireRole.class);
        if (require == null) {
            require = method.getBeanType().getAnnotation(RequireRole.class);
        }
        if (require != null) {
            int need = ROLE_RANK.getOrDefault(require.value(), Integer.MAX_VALUE);
            int has = ROLE_RANK.getOrDefault(UserContext.get().getRole(), 0);
            if (has < need) {
                return reject(response, 403, "权限不足，需要 " + require.value() + " 及以上角色");
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        UserContext.clear();
    }

    private boolean reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(status, message)));
        return false;
    }
}
