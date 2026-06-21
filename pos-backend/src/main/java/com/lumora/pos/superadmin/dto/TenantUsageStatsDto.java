package com.lumora.pos.superadmin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantUsageStatsDto {
    private long activeLocations;
    private long activeUsers;
    private long totalProducts;
    private long totalOrders;
    private BigDecimal lifetimeRevenue;
}
