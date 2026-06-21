package com.lumora.pos.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private UserDto user;

    /**
     * When true, accessToken is a short-lived token scoped to
     * POST /api/v1/auth/change-password — the client must force the user
     * through a password change before entering the app. No refresh token is issued.
     */
    @lombok.Builder.Default
    private boolean passwordChangeRequired = false;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDto {
        private UUID id;
        private UUID tenantId;
        private String email;
        private String firstName;
        private String lastName;
        private List<String> roles;
        private List<String> permissions;
        private List<String> featuresEnabled;
        private String planTier;
        private Integer maxLocations;
        private Integer maxUsers;
        private Integer maxProducts;
    }
}
