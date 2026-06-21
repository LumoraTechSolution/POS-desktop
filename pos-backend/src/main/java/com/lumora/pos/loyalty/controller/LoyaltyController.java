package com.lumora.pos.loyalty.controller;

import com.lumora.pos.common.dto.ApiResponse;
import com.lumora.pos.loyalty.dto.LoyaltyTransactionResponse;
import com.lumora.pos.loyalty.service.LoyaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loyalty")
@RequiredArgsConstructor
public class LoyaltyController {

    private final LoyaltyService loyaltyService;

    /** A customer's loyalty points ledger (earn/redeem history), newest first. */
    @GetMapping("/customers/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    public ResponseEntity<ApiResponse<Page<LoyaltyTransactionResponse>>> getLedger(
            @PathVariable UUID customerId,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                loyaltyService.getLedger(customerId, pageable),
                "Loyalty ledger fetched successfully"));
    }
}
