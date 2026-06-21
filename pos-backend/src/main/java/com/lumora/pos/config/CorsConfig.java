package com.lumora.pos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS configuration for cross-origin requests from the Next.js frontend.
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> patterns = List.of(allowedOrigins.split(","));
        // Guard: a bare "*" with credentials lets ANY site make authenticated
        // requests with the user's cookies/token — a data-theft vector. Origins
        // must be explicit or tightly-scoped patterns (e.g. http://*.localhost:3000).
        if (patterns.stream().map(String::trim).anyMatch("*"::equals)) {
            throw new IllegalStateException(
                    "app.cors.allowed-origins must not be a bare '*' while credentials are allowed; "
                            + "use explicit origins or a scoped pattern like https://*.yourpos.com");
        }

        CorsConfiguration configuration = new CorsConfiguration();
        // Origin *patterns* (not setAllowedOrigins): the only way to combine wildcard
        // subdomains with allowCredentials — Spring validates the Origin and echoes it
        // back instead of sending "*". Exact origins are still valid patterns, so this
        // is backward-compatible with existing single-origin config.
        configuration.setAllowedOriginPatterns(patterns);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Tenant-ID", "X-Tenant-Domain", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Authorization", "X-Total-Count"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
