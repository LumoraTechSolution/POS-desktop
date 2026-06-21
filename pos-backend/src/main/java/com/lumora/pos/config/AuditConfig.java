package com.lumora.pos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA Auditing configuration.
 * Provides the current user ID for @CreatedBy and @LastModifiedBy fields.
 * In Step 2, this will read from SecurityContext (JWT-authenticated user).
 */
@Configuration
public class AuditConfig {

    @Bean
    public AuditorAware<UUID> auditorProvider() {
        return () -> {
            org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated() ||
                    !(authentication.getPrincipal() instanceof UUID)) {
                return Optional.empty();
            }

            return Optional.of((UUID) authentication.getPrincipal());
        };
    }
}
