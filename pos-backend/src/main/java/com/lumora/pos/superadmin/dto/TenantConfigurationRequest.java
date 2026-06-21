package com.lumora.pos.superadmin.dto;

import com.lumora.pos.superadmin.enums.PlanTier;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Request DTO for creating or updating a tenant's configuration.
 * Used by super admin endpoints to govern tenant subscriptions.
 */
@Data
public class TenantConfigurationRequest {

    @NotNull(message = "Plan tier is required")
    private PlanTier planTier;

    @Min(value = 1, message = "Max locations must be at least 1")
    @Max(value = 999, message = "Max locations cannot exceed 999")
    private int maxLocations;

    @Min(value = 1, message = "Max users must be at least 1")
    @Max(value = 999, message = "Max users cannot exceed 999")
    private int maxUsers;

    @Min(value = 1, message = "Max products must be at least 1")
    @Max(value = 999999, message = "Max products cannot exceed 999999")
    private int maxProducts;

    @NotEmpty(message = "At least one feature must be enabled")
    private List<String> featuresEnabled;

    private boolean isActive;

    private LocalDateTime subscriptionEnd;

    private String notes;
}
