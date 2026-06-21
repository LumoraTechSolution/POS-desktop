package com.lumora.pos.superadmin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Profile + session-anomaly surface for the super admin's account page.
 * Returned by GET /api/v1/super-admin/auth/me.
 *
 * Last-login fields are populated by SuperAdminAuthService.login() and
 * let the operator spot a session from an unfamiliar IP / UA.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuperAdminProfileResponse {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private String role;

    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private String lastLoginUserAgent;

    private LocalDateTime createdAt;
}
