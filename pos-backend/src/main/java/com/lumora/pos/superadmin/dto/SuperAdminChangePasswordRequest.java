package com.lumora.pos.superadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SuperAdminChangePasswordRequest {

    /**
     * Current password. Required for self-service rotations; for the
     * forced-on-first-login flow the short-lived password-change token
     * is the proof of identity, but the user still re-enters it as a
     * confirmation.
     */
    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 12, message = "New password must be at least 12 characters")
    private String newPassword;
}
