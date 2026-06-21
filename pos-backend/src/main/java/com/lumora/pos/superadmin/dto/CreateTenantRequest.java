package com.lumora.pos.superadmin.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Request DTO for provisioning a brand new tenant.
 * Creates the tenant row AND the default tenant_configuration row.
 */
@Data
public class CreateTenantRequest {

    @NotBlank(message = "Business name is required")
    @Size(max = 255, message = "Business name cannot exceed 255 characters")
    private String name;

    @Size(max = 255, message = "Domain cannot exceed 255 characters")
    private String domain;

    /** Initial plan tier. Defaults to SMALL_BUSINESS if not provided. */
    private String planTier;

    /** Admin user details for the first user in the new tenant. */
    @NotBlank(message = "Admin email is required")
    @Email(message = "Must be a valid email address")
    private String adminEmail;

    @NotBlank(message = "Admin first name is required")
    private String adminFirstName;

    @NotBlank(message = "Admin last name is required")
    private String adminLastName;

    @NotBlank(message = "Admin password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String adminPassword;

    /** Optional internal notes. */
    private String notes;
}
