package com.lumora.pos.auth.entity;

import com.lumora.pos.branch.entity.BranchEntity;
import com.lumora.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * JPA entity mapping the `users` table.
 * Extends BaseEntity for id, tenantId, and audit fields.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /** BCrypt-hashed 4-digit PIN for cashier quick-login */
    @Column(length = 255)
    private String pin;

    /**
     * Keyed blind index of the PIN (HMAC-SHA256, hex). Deterministic, so equal
     * PINs share a value — used to detect collisions and enforce uniqueness
     * without brute-forcing the salted bcrypt {@link #pin}. Null until the PIN
     * is next set. See V54 and {@code PinLookupHasher}.
     */
    @Column(name = "pin_lookup", length = 64)
    private String pinLookup;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(length = 20)
    private String phone;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * When TRUE, login succeeds but only issues a short-lived token scoped to
     * POST /api/v1/auth/change-password — the user must rotate an admin-set
     * password before doing anything else. Set on tenant provisioning and on
     * admin/super-admin password resets. Mirrors SuperAdmin.passwordChangeRequired.
     */
    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    @Builder.Default
    private Set<RoleEntity> roles = new HashSet<>();

    /** Primary branch this user works at. Null for back-office users with no branch context. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_branch_id")
    private BranchEntity primaryBranch;

    /** All branches this user may operate at (used by branch-access enforcement). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_branches", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "branch_id"))
    @Builder.Default
    private Set<BranchEntity> branches = new HashSet<>();
}
