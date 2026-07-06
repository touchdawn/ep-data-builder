package com.ep.databuilder.config;

import com.ep.databuilder.security.AuthInterceptor;
import com.ep.databuilder.security.OpenTokenInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final OpenTokenInterceptor openTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/v1/auth/login");
        registry.addInterceptor(openTokenInterceptor)
                .addPathPatterns("/open-api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 前端开发走 vite 代理，此处放开便于直连调试
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("*")
                .allowedHeaders("*");
    }
}
