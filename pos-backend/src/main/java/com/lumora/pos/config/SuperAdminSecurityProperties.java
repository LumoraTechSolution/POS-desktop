package com.lumora.pos.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Super-admin security tunables. Bound from {@code app.security.super-admin.*}.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.security.super-admin")
public class SuperAdminSecurityProperties {

    /** Failed-password attempts before the account is temporarily locked. */
    private int maxFailedAttempts = 5;

    /** How long a locked account stays locked. */
    private int lockoutDurationMinutes = 15;

    /** Lifetime of the short-lived token issued when {@code password_change_required} is set. */
    private int passwordChangeTokenTtlMinutes = 5;
}
