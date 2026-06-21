package com.lumora.pos.finance.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class FinanceDtos {

    @Data
    @Builder
    public static class ProfitLossReport {
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private BigDecimal revenue;
        private BigDecimal costOfGoodsSold;
        private BigDecimal grossProfit;
        private BigDecimal grossMarginPct;
        private BigDecimal operatingExpenses;
        private BigDecimal netProfit;
        private BigDecimal netMarginPct;
        private List<CategoryAmount> expenseBreakdown;
    }

    @Data
    @Builder
    public static class CategoryAmount {
        private UUID categoryId;
        private String categoryName;
        private BigDecimal amount;
    }

    @Data
    @Builder
    public static class CashFlowReport {
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private BigDecimal totalInflow;
        private BigDecimal totalOutflow;
        private BigDecimal netCashFlow;
        /** Avg monthly net burn over the period; null/0 when not burning cash. */
        private BigDecimal avgMonthlyNetBurn;
        /** Months of runway at current cash position ÷ burn; null when not burning. */
        private BigDecimal runwayMonths;
        private List<CashFlowPeriod> series;
    }

    @Data
    @Builder
    public static class CashFlowPeriod {
        private String label;       // e.g. "2026-06"
        private BigDecimal inflow;   // sales receipts
        private BigDecimal outflow;  // operating expenses + inventory (PO) spend
        private BigDecimal net;
    }
}
