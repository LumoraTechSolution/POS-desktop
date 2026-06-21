package com.lumora.pos.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The authenticated tenant user's own profile, returned by GET /api/v1/auth/me
 * and rendered on the POS profile page.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyProfileResponse {
    private UUID id;
    private UUID tenantId;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private List<String> roles;
    /** Whether a 4-digit PIN is set (used to label the Change-PIN action). */
    private boolean hasPin;
    private UUID primaryBranchId;
    private String primaryBranchName;
    private List<BranchRef> branches;
    private LocalDateTime lastLoginAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BranchRef {
        private UUID id;
        private String name;
    }
}
