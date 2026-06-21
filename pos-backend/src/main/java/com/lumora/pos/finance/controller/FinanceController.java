package com.lumora.pos.finance.controller;

import com.lumora.pos.common.dto.ApiResponse;
import com.lumora.pos.finance.dto.FinanceDtos.CashFlowReport;
import com.lumora.pos.finance.dto.FinanceDtos.ProfitLossReport;
import com.lumora.pos.finance.service.FinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService financeService;

    @GetMapping("/profit-loss")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ProfitLossReport>> getProfitAndLoss(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) java.util.UUID branchId) {
        return ResponseEntity.ok(ApiResponse.success(
                financeService.getProfitAndLoss(start, end, branchId), "Profit & loss retrieved"));
    }

    @GetMapping("/cash-flow")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<CashFlowReport>> getCashFlow(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) java.util.UUID branchId) {
        return ResponseEntity.ok(ApiResponse.success(
                financeService.getCashFlow(start, end, branchId), "Cash flow retrieved"));
    }
}
