package com.lumora.pos.inventory.controller;

import com.lumora.pos.common.dto.ApiResponse;
import com.lumora.pos.inventory.dto.StockTransferRequest;
import com.lumora.pos.inventory.dto.StockTransferResponse;
import com.lumora.pos.inventory.entity.StockTransferEntity.TransferStatus;
import com.lumora.pos.inventory.service.StockTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock-transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService stockTransferService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> createTransfer(
            @Valid @RequestBody StockTransferRequest request) {
        StockTransferResponse response = stockTransferService.createTransfer(request);
        return ResponseEntity.status(201).body(
                ApiResponse.success(response, "Stock transfer created successfully"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<Page<StockTransferResponse>>> getTransfers(
            @RequestParam(required = false) TransferStatus status,
            Pageable pageable) {
        Page<StockTransferResponse> page = stockTransferService.getTransfers(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(page, "Transfers fetched successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getTransferById(@PathVariable UUID id) {
        StockTransferResponse response = stockTransferService.getTransferById(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Transfer fetched successfully"));
    }

    @PutMapping("/{id}/in-transit")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> markInTransit(@PathVariable UUID id) {
        StockTransferResponse response = stockTransferService.updateStatus(id, TransferStatus.IN_TRANSIT);
        return ResponseEntity.ok(ApiResponse.success(response, "Transfer marked as in-transit"));
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> completeTransfer(@PathVariable UUID id) {
        StockTransferResponse response = stockTransferService.updateStatus(id, TransferStatus.COMPLETED);
        return ResponseEntity.ok(ApiResponse.success(response, "Transfer completed — stock moved successfully"));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> cancelTransfer(@PathVariable UUID id) {
        StockTransferResponse response = stockTransferService.updateStatus(id, TransferStatus.CANCELLED);
        return ResponseEntity.ok(ApiResponse.success(response, "Transfer cancelled successfully"));
    }
}
