package com.lumora.pos.superadmin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SuperAdminRefreshRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
