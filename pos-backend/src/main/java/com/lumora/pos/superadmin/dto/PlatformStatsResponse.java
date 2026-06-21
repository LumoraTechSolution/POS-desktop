package com.lumora.pos.superadmin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Platform-wide statistics DTO.
 * Returned by GET /api/v1/super-admin/stats
 * Gives the super admin an overview of the platform health.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformStatsResponse {

    // Tenant counts
    private long totalTenants;
    private long activeTenants;
    private long suspendedTenants;

    // Tier breakdown
    private long smallBusinessCount;
    private long mediumBusinessCount;
    private long enterpriseCount;

    // Subscription health
    private long expiredSubscriptions;

    // Financial
    private java.math.BigDecimal projectedMrr;

    // Activity KPIs (P3.1)
    /** Distinct tenant users with any audit_log entry in the last 30 days. */
    private long monthlyActiveUsers;
    /** Number of TENANT_SUSPENDED events in the calendar month-to-date. */
    private long churnThisMonth;
    /** Number of TENANT_CONFIG_UPDATED events in the last 30 days where planTier changed. */
    private long planChanges30d;
}
