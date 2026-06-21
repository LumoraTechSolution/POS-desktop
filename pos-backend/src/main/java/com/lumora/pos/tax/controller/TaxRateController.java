package com.lumora.pos.tax.controller;

import com.lumora.pos.common.dto.ApiResponse;
import com.lumora.pos.tax.dto.TaxRateRequest;
import com.lumora.pos.tax.dto.TaxRateResponse;
import com.lumora.pos.tax.service.TaxRateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tax-rates")
@RequiredArgsConstructor
public class TaxRateController {

    private final TaxRateService taxRateService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<TaxRateResponse>>> getAllTaxRates() {
        return ResponseEntity.ok(ApiResponse.success(taxRateService.getAllTaxRates()));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    public ResponseEntity<ApiResponse<List<TaxRateResponse>>> getActiveTaxRates() {
        return ResponseEntity.ok(ApiResponse.success(taxRateService.getActiveTaxRates()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<TaxRateResponse>> getTaxRateById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(taxRateService.getTaxRateById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TaxRateResponse>> createTaxRate(@Valid @RequestBody TaxRateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                taxRateService.createTaxRate(request), "Tax rate created successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TaxRateResponse>> updateTaxRate(
            @PathVariable UUID id,
            @Valid @RequestBody TaxRateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                taxRateService.updateTaxRate(id, request), "Tax rate updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteTaxRate(@PathVariable UUID id) {
        taxRateService.deleteTaxRate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Tax rate deleted successfully")
                .build());
    }
}
