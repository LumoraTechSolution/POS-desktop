package com.lumora.pos.inventory.controller;

import com.lumora.pos.common.dto.ApiResponse;
import com.lumora.pos.inventory.dto.InventoryAdjustmentRequest;
import com.lumora.pos.inventory.dto.InventoryAdjustmentResponse;
import com.lumora.pos.inventory.dto.StockTransferRequest;
import com.lumora.pos.inventory.service.InventoryAdjustmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryAdjustmentController {

    private final InventoryAdjustmentService adjustmentService;

    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> adjustStock(@RequestBody InventoryAdjustmentRequest request) {
        adjustmentService.adjustStock(request);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Inventory adjusted successfully")
                .build());
    }

    @PostMapping("/transfer")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> transferStock(@RequestBody StockTransferRequest request) {
        adjustmentService.transferStock(request);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Stock transferred successfully")
                .build());
    }

    @GetMapping("/adjustments/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<List<InventoryAdjustmentResponse>>> getAdjustments(@PathVariable UUID productId) {
        return ResponseEntity.ok(ApiResponse.success(
                adjustmentService.getAdjustmentsByProduct(productId),
                "Adjustments fetched successfully"));
    }
}
