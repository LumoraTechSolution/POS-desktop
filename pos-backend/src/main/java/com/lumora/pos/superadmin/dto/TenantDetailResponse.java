package com.lumora.pos.superadmin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lumora.pos.superadmin.enums.PlanTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Full detail DTO for a single tenant — returned in the tenant detail view.
 * Contains all configuration, subscription info, and usage details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantDetailResponse {

    // Tenant core info
    private UUID id;
    private String name;
    private String domain;
    private boolean tenantActive;
    private LocalDateTime createdAt;

    // Business profile (what the tenant set under Settings → for support/visibility)
    private String addressLine1;
    private String addressLine2;
    private String phone;
    private String logoUrl;
    private String receiptFooter;

    // Configuration
    private UUID configId;
    private PlanTier planTier;
    private int maxLocations;
    private int maxUsers;
    private int maxProducts;
    private List<String> featuresEnabled;

    // Subscription
    private boolean configActive;
    private LocalDateTime subscriptionStart;
    private LocalDateTime subscriptionEnd;
    // Match the wire format used by TenantSummaryResponse so the frontend
    // can keep a single type for the "subscription expired" flag across
    // list and detail endpoints.
    @JsonProperty("isSubscriptionExpired")
    private boolean isSubscriptionExpired;
    private String notes;

    // Computed stats
    private LocalDateTime configCreatedAt;
    private LocalDateTime configUpdatedAt;
    
    // Live usage stats mapping for frontend Overview Tab
    private TenantUsageStatsDto usage;
}
