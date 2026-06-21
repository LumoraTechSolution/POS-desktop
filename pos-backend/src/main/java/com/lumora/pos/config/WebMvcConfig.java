package com.lumora.pos.config;

import com.lumora.pos.superadmin.interceptor.FeatureGuardInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers global MVC interceptors.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final FeatureGuardInterceptor featureGuardInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Register the FeatureGuardInterceptor to run on all API routes.
        // Public endpoints and /actuator are excluded to avoid unnecessary processing.
        registry.addInterceptor(featureGuardInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/auth/**",
                        "/api/v1/public/**",
                        "/api/v1/super-admin/auth/**"
                );
    }
}
