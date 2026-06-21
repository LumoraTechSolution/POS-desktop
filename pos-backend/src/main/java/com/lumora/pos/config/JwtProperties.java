package com.lumora.pos.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds app.jwt.* properties from application.yml.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** HS256 signing secret — must be ≥ 256 bits in production */
    private String secret;

    /** Access token lifetime in milliseconds (default 24h) */
    private long expirationMs = 86_400_000L;

    /** Refresh token lifetime in milliseconds (default 7 days) */
    private long refreshExpirationMs = 604_800_000L;

    @jakarta.annotation.PostConstruct
    public void validate() {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException("CRITICAL SECURITY ERROR: JWT secret must be configured! Set the JWT_SECRET environment variable.");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("CRITICAL SECURITY ERROR: JWT secret is too short. It must be at least 32 characters (256 bits) for HS256.");
        }
    }
}
