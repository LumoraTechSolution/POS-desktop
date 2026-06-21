package com.lumora.pos.purchase.controller;

import com.lumora.pos.common.dto.ApiResponse;

import com.lumora.pos.purchase.dto.PurchaseOrderRequest;
import com.lumora.pos.purchase.dto.PurchaseOrderResponse;
import com.lumora.pos.purchase.dto.ReceivePoItemRequest;
import com.lumora.pos.purchase.entity.PurchaseOrderEntity;
import com.lumora.pos.purchase.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService poService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> createPO(
            @Valid @RequestBody PurchaseOrderRequest request) {
        PurchaseOrderResponse response = poService.createPurchaseOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Purchase order created successfully"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<Page<PurchaseOrderResponse>>> getAllPOs(
            @RequestParam(required = false) PurchaseOrderEntity.POStatus status,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String[] sort) {

        Sort.Direction direction = sort[1].equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(direction, sort[0]));

        Page<PurchaseOrderResponse> responsePage = poService.getAllPOs(status, supplierId, search, pageRequest);
        return ResponseEntity
                .ok(ApiResponse.success(responsePage, "Purchase orders retrieved successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> getPOById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(poService.getPOById(id), "Purchase order retrieved successfully"));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> updatePOStatus(
            @PathVariable UUID id,
            @RequestParam PurchaseOrderEntity.POStatus status) {
        return ResponseEntity
                .ok(ApiResponse.success(poService.updatePOStatus(id, status), "Purchase order status updated"));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> receivePO(
            @PathVariable UUID id,
            @Valid @RequestBody List<ReceivePoItemRequest> items) {
        return ResponseEntity.ok(ApiResponse.success(poService.receivePurchaseOrder(id, items),
                "Purchase order items received successfully"));
    }
}
