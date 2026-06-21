package com.lumora.pos.superadmin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Platform-level super admin account.
 *
 * IMPORTANT: This entity does NOT extend BaseEntity.
 * Super admins are NOT scoped to any tenant. They operate
 * at the platform level and can manage ALL tenants.
 *
 * Maps to the `super_admins` table (created in V24 migration).
 */
@Entity
@Table(name = "super_admins")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuperAdminEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;

    @Column(name = "last_login_user_agent", length = 500)
    private String lastLoginUserAgent;

    /** Per-account failed-password counter; complements RateLimitFilter (per-IP). */
    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private int failedLoginAttempts = 0;

    /** When set in the future, login is rejected with a lockout error until this passes. */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /**
     * When TRUE, login succeeds but only issues a short-lived token scoped to
     * /super-admin/auth/change-password. All other endpoints reject the token.
     * Backfilled to TRUE in V40 so the seeded default password must be rotated.
     */
    @Column(name = "password_change_required", nullable = false)
    @Builder.Default
    private boolean passwordChangeRequired = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
