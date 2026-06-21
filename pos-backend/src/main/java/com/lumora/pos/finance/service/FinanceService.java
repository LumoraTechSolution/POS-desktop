package com.lumora.pos.finance.service;

import com.lumora.pos.branch.service.BranchAccessGuard;
import com.lumora.pos.expense.repository.ExpenseRepository;
import com.lumora.pos.finance.dto.FinanceDtos.CashFlowPeriod;
import com.lumora.pos.finance.dto.FinanceDtos.CashFlowReport;
import com.lumora.pos.finance.dto.FinanceDtos.CategoryAmount;
import com.lumora.pos.finance.dto.FinanceDtos.ProfitLossReport;
import com.lumora.pos.purchase.entity.PurchaseOrderEntity;
import com.lumora.pos.purchase.repository.PurchaseOrderRepository;
import com.lumora.pos.report.dto.ReportDtos.ProfitabilityReport;
import com.lumora.pos.report.service.ReportService;
import com.lumora.pos.sales.repository.SaleRepository;
import com.lumora.pos.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinanceService {

    private final ReportService reportService;
    private final ExpenseRepository expenseRepository;
    private final SaleRepository saleRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final BranchAccessGuard branchAccessGuard;

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final LocalDate EPOCH = LocalDate.of(2000, 1, 1);

    /**
     * Net P&L for a period: revenue − COGS − operating expenses.
     * Reuses {@link ReportService#getProfitabilityReport} for the sales side
     * (revenue/COGS/gross) and adds operating expenses on top.
     */
    @Transactional(readOnly = true)
    public ProfitLossReport getProfitAndLoss(LocalDate start, LocalDate end, UUID branchId) {
        UUID tenantId = TenantContext.getTenantId();
        Optional<Set<UUID>> branchFilter = branchAccessGuard.reportBranchFilter(branchId);

        // Sales side — only the summary totals are needed, so request a 1-row page.
        ProfitabilityReport sales = reportService.getProfitabilityReport(
                start.atStartOfDay(), end.atTime(LocalTime.MAX), branchId, PageRequest.of(0, 1));

        BigDecimal revenue = nz(sales.getTotalRevenue());
        BigDecimal cogs = nz(sales.getTotalCost());
        BigDecimal grossProfit = nz(sales.getTotalProfit());

        BigDecimal opex = nz(sumExpenses(tenantId, start, end, branchFilter));
        BigDecimal netProfit = grossProfit.subtract(opex);

        List<Object[]> categoryRows = branchFilter.isPresent()
                ? expenseRepository.sumByCategoryAndBranch(tenantId, start, end, branchFilter.get())
                : expenseRepository.sumByCategory(tenantId, start, end);
        List<CategoryAmount> breakdown = categoryRows.stream()
                .map(row -> CategoryAmount.builder()
                        .categoryId((UUID) row[0])
                        .categoryName((String) row[1])
                        .amount(nz((BigDecimal) row[2]))
                        .build())
                .toList();

        return ProfitLossReport.builder()
                .periodStart(start)
                .periodEnd(end)
                .revenue(revenue)
                .costOfGoodsSold(cogs)
                .grossProfit(grossProfit)
                .grossMarginPct(marginPct(grossProfit, revenue))
                .operatingExpenses(opex)
                .netProfit(netProfit)
                .netMarginPct(marginPct(netProfit, revenue))
                .expenseBreakdown(breakdown)
                .build();
    }

    /**
     * Cash flow: money in (sales receipts) vs money out (operating expenses +
     * inventory/PO spend), bucketed by calendar month. Runway estimates how long
     * the cash generated to date lasts at the current burn rate — based on
     * recorded sales/expenses, not a live bank balance.
     */
    @Transactional(readOnly = true)
    public CashFlowReport getCashFlow(LocalDate start, LocalDate end, UUID branchId) {
        UUID tenantId = TenantContext.getTenantId();
        Optional<Set<UUID>> branchFilter = branchAccessGuard.reportBranchFilter(branchId);

        List<CashFlowPeriod> series = new ArrayList<>();
        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;

        YearMonth cursor = YearMonth.from(start);
        YearMonth last = YearMonth.from(end);
        int months = 0;
        while (!cursor.isAfter(last)) {
            LocalDate mStart = cursor.atDay(1).isBefore(start) ? start : cursor.atDay(1);
            LocalDate mEnd = cursor.atEndOfMonth().isAfter(end) ? end : cursor.atEndOfMonth();

            BigDecimal inflow = nz(sumSalesInflow(tenantId, mStart, mEnd, branchFilter));
            BigDecimal outflow = nz(sumExpenses(tenantId, mStart, mEnd, branchFilter))
                    .add(nz(sumPurchaseSpend(tenantId, mStart, mEnd, branchFilter)));

            series.add(CashFlowPeriod.builder()
                    .label(cursor.toString())
                    .inflow(inflow)
                    .outflow(outflow)
                    .net(inflow.subtract(outflow))
                    .build());

            totalIn = totalIn.add(inflow);
            totalOut = totalOut.add(outflow);
            months++;
            cursor = cursor.plusMonths(1);
        }

        BigDecimal netCashFlow = totalIn.subtract(totalOut);

        // Burn = average monthly net deficit over the period (null when net-positive).
        BigDecimal avgMonthlyNet = months == 0 ? BigDecimal.ZERO
                : netCashFlow.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        BigDecimal burn = avgMonthlyNet.signum() < 0 ? avgMonthlyNet.negate() : null;

        BigDecimal runway = null;
        if (burn != null) {
            BigDecimal cashPosition = cashPositionUpTo(tenantId, end, branchFilter);
            if (cashPosition.signum() > 0) {
                runway = cashPosition.divide(burn, 1, RoundingMode.HALF_UP);
            }
        }

        return CashFlowReport.builder()
                .periodStart(start)
                .periodEnd(end)
                .totalInflow(totalIn)
                .totalOutflow(totalOut)
                .netCashFlow(netCashFlow)
                .avgMonthlyNetBurn(burn)
                .runwayMonths(runway)
                .series(series)
                .build();
    }

    /** Estimated cash on hand = all recorded inflows − outflows up to the given date. */
    private BigDecimal cashPositionUpTo(UUID tenantId, LocalDate end, Optional<Set<UUID>> branchFilter) {
        BigDecimal in = nz(sumSalesInflow(tenantId, EPOCH, end, branchFilter));
        BigDecimal out = nz(sumExpenses(tenantId, EPOCH, end, branchFilter))
                .add(nz(sumPurchaseSpend(tenantId, EPOCH, end, branchFilter)));
        return in.subtract(out);
    }

    // ─── Branch-aware money sums (filter present → branch-scoped query, absent → all) ───

    private BigDecimal sumSalesInflow(UUID tenantId, LocalDate start, LocalDate end, Optional<Set<UUID>> branchFilter) {
        return branchFilter.isPresent()
                ? saleRepository.sumNetAmountByTenantIdAndDateRangeAndBranch(
                        tenantId, start.atStartOfDay(), end.atTime(LocalTime.MAX), branchFilter.get())
                : saleRepository.sumNetAmountByTenantIdAndDateRange(
                        tenantId, start.atStartOfDay(), end.atTime(LocalTime.MAX));
    }

    private BigDecimal sumExpenses(UUID tenantId, LocalDate start, LocalDate end, Optional<Set<UUID>> branchFilter) {
        return branchFilter.isPresent()
                ? expenseRepository.sumByPeriodAndBranch(tenantId, start, end, branchFilter.get())
                : expenseRepository.sumByPeriod(tenantId, start, end);
    }

    private BigDecimal sumPurchaseSpend(UUID tenantId, LocalDate start, LocalDate end, Optional<Set<UUID>> branchFilter) {
        return branchFilter.isPresent()
                ? purchaseOrderRepository.sumByStatusAndPeriodAndBranch(
                        tenantId, PurchaseOrderEntity.POStatus.RECEIVED,
                        start.atStartOfDay(), end.atTime(LocalTime.MAX), branchFilter.get())
                : purchaseOrderRepository.sumByStatusAndPeriod(
                        tenantId, PurchaseOrderEntity.POStatus.RECEIVED,
                        start.atStartOfDay(), end.atTime(LocalTime.MAX));
    }

    private BigDecimal marginPct(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) return BigDecimal.ZERO;
        return part.divide(whole, 4, RoundingMode.HALF_UP).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
