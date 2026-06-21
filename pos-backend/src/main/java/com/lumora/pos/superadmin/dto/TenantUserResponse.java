package com.lumora.pos.superadmin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A tenant user as seen by the Super Admin (tenant detail → Users tab).
 * This is also the "forgot email" lookup surface — the super admin can read a
 * user's email to relay it back to them. No secrets are exposed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantUserResponse {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private boolean active;
    private boolean mustChangePassword;
    private List<String> roles;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
