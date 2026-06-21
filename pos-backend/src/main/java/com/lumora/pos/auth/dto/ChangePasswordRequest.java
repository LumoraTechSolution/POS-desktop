package com.lumora.pos.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Self-service password change for a tenant user. Used both by the
 * forced-on-first-login flow (with a USER_PWCHANGE token) and by ordinary
 * self-service rotations from the profile page (with a normal USER token).
 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String newPassword;
}
