package com.lumora.pos.finance.service;

import com.lumora.pos.branch.service.BranchAccessGuard;
import com.lumora.pos.expense.repository.ExpenseRepository;
import com.lumora.pos.finance.dto.FinanceDtos.CashFlowReport;
import com.lumora.pos.finance.dto.FinanceDtos.ProfitLossReport;
import com.lumora.pos.purchase.entity.PurchaseOrderEntity;
import com.lumora.pos.purchase.repository.PurchaseOrderRepository;
import com.lumora.pos.report.dto.ReportDtos.ProfitabilityReport;
import com.lumora.pos.report.service.ReportService;
import com.lumora.pos.sales.repository.SaleRepository;
import com.lumora.pos.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceServiceTest {

    @Mock ReportService reportService;
    @Mock ExpenseRepository expenseRepository;
    @Mock SaleRepository saleRepository;
    @Mock PurchaseOrderRepository purchaseOrderRepository;
    @Mock BranchAccessGuard branchAccessGuard;

    @InjectMocks FinanceService financeService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void profitAndLoss_subtractsOperatingExpensesFromGross() {
        ProfitabilityReport sales = ProfitabilityReport.builder()
                .totalRevenue(new BigDecimal("1000.00"))
                .totalCost(new BigDecimal("400.00"))
                .totalProfit(new BigDecimal("600.00"))
                .build();
        when(branchAccessGuard.reportBranchFilter(any())).thenReturn(Optional.empty());
        when(reportService.getProfitabilityReport(any(), any(), any(), any())).thenReturn(sales);
        when(expenseRepository.sumByPeriod(any(), any(), any())).thenReturn(new BigDecimal("150.00"));
        when(expenseRepository.sumByCategory(any(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{UUID.randomUUID(), "Rent", new BigDecimal("150.00")}));

        ProfitLossReport pnl = financeService.getProfitAndLoss(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null);

        assertThat(pnl.getRevenue()).isEqualByComparingTo("1000.00");
        assertThat(pnl.getCostOfGoodsSold()).isEqualByComparingTo("400.00");
        assertThat(pnl.getGrossProfit()).isEqualByComparingTo("600.00");
        assertThat(pnl.getGrossMarginPct()).isEqualByComparingTo("60.00");
        assertThat(pnl.getOperatingExpenses()).isEqualByComparingTo("150.00");
        assertThat(pnl.getNetProfit()).isEqualByComparingTo("450.00");   // 600 − 150
        assertThat(pnl.getNetMarginPct()).isEqualByComparingTo("45.00"); // 450 / 1000
        assertThat(pnl.getExpenseBreakdown()).hasSize(1);
    }

    @Test
    void cashFlow_outflowIncludesExpensesAndPurchaseOrders() {
        when(branchAccessGuard.reportBranchFilter(any())).thenReturn(Optional.empty());
        when(saleRepository.sumNetAmountByTenantIdAndDateRange(any(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("1000.00"));
        when(expenseRepository.sumByPeriod(any(), any(), any())).thenReturn(new BigDecimal("1200.00"));
        when(purchaseOrderRepository.sumByStatusAndPeriod(any(), any(PurchaseOrderEntity.POStatus.class), any(), any()))
                .thenReturn(new BigDecimal("300.00"));

        CashFlowReport cf = financeService.getCashFlow(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null);

        assertThat(cf.getSeries()).hasSize(1);
        assertThat(cf.getTotalInflow()).isEqualByComparingTo("1000.00");
        assertThat(cf.getTotalOutflow()).isEqualByComparingTo("1500.00"); // 1200 expenses + 300 PO
        assertThat(cf.getNetCashFlow()).isEqualByComparingTo("-500.00");
        assertThat(cf.getAvgMonthlyNetBurn()).isEqualByComparingTo("500.00"); // burning
        assertThat(cf.getRunwayMonths()).isNull(); // estimated cash position is negative
    }
}
