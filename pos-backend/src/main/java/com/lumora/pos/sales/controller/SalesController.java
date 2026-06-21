package com.lumora.pos.sales.controller;

import com.lumora.pos.common.dto.ApiResponse;
import com.lumora.pos.sales.dto.PaymentCorrectionRequest;
import com.lumora.pos.sales.dto.SaleRequest;
import com.lumora.pos.sales.dto.SaleResponse;
import com.lumora.pos.sales.dto.SalesSummaryResponse;
import com.lumora.pos.sales.service.SaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
public class SalesController {

    private final SaleService saleService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    public ResponseEntity<ApiResponse<SaleResponse>> createSale(@Valid @RequestBody SaleRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(
                saleService.createSale(request),
                "Sale processed successfully"));
    }

    @GetMapping("/summary/daily")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    public ResponseEntity<ApiResponse<SalesSummaryResponse>> getDailySummary() {
        return ResponseEntity.ok(ApiResponse.success(
                saleService.getDailySummary(),
                "Daily summary fetched successfully"));
    }

    /**
     * Corrects payment metadata (method and/or cash tendered) on a recently
     * completed sale. Service-layer rules:
     *  - sale must be PAID and attached to an OPEN cash session;
     *  - cashier may self-serve their own sale within a 5-minute window,
     *    otherwise a MANAGER/ADMIN PIN is required in the body.
     */
    @PatchMapping("/{id}/payment-correction")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    public ResponseEntity<ApiResponse<SaleResponse>> correctPayment(
            @PathVariable UUID id,
            @Valid @RequestBody PaymentCorrectionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                saleService.correctPayment(id, request),
                "Sale payment corrected"));
    }

    /**
     * Sales from the current cashier's open shift, newest first. Backs the
     * terminal's payment-correction picker so a cashier (or a manager via PIN)
     * can correct any sale in the active session, not just the last one.
     */
    @GetMapping("/session/current")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    public ResponseEntity<ApiResponse<java.util.List<SaleResponse>>> getCurrentSessionSales() {
        return ResponseEntity.ok(ApiResponse.success(
                saleService.getCurrentSessionSales(),
                "Current session sales fetched successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    public ResponseEntity<ApiResponse<SaleResponse>> getSale(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                saleService.getSaleById(id),
                "Sale fetched successfully"));
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<SaleResponse>>> getSalesByCustomer(
            @PathVariable UUID customerId,
            org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                saleService.getSalesByCustomer(customerId, pageable),
                "Customer sales fetched successfully"));
    }
}
