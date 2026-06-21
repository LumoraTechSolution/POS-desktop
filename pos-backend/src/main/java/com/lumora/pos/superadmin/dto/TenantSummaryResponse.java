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
 * Summary DTO for a tenant — returned in the paginated tenant list.
 * Contains essential info without full feature/config details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSummaryResponse {

    private UUID id;
    private String name;
    private String domain;

    // Configuration summary
    private PlanTier planTier;
    @JsonProperty("isActive")
    private boolean isActive;
    @JsonProperty("isSubscriptionExpired")
    private boolean isSubscriptionExpired;
    private LocalDateTime subscriptionEnd;

    // Usage snapshot
    private int maxLocations;
    private int maxUsers;
    private int maxProducts;

    private LocalDateTime createdAt;
}
