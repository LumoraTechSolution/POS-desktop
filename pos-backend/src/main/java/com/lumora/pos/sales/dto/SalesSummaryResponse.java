package com.lumora.pos.sales.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class SalesSummaryResponse {
    private int totalOrders;
    private BigDecimal totalGrossSales;
    private BigDecimal totalTax;
    private BigDecimal totalDiscounts;
    private BigDecimal totalNetSales;
    private Map<String, BigDecimal> salesByPaymentMethod;
}
