package com.lumora.pos.superadmin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO returned after successful Super Admin authentication.
 * Contains the JWT access token and basic super admin profile info.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuperAdminAuthResponse {

    private String accessToken;
    /**
     * Opaque server-side refresh token. Stored in sessionStorage on the
     * client (matches tenant authStore pattern) and exchanged at
     * POST /super-admin/auth/refresh for a new access token.
     * Null when the response carries a password-change token instead of
     * a full session token.
     */
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    /**
     * True when the super admin must rotate their password before they
     * can use the rest of the API. The accessToken in this case is
     * scoped to POST /super-admin/auth/change-password only.
     */
    private boolean passwordChangeRequired;
    private SuperAdminDto superAdmin;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuperAdminDto {
        private UUID id;
        private String email;
        private String firstName;
        private String lastName;
        private String fullName;
        /** Always "SUPERADMIN" — included for frontend role checks. */
        private String role;
        /** Mirrors top-level flag for convenience in the frontend store. */
        private boolean passwordChangeRequired;
    }
}
