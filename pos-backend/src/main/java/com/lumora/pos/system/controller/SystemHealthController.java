package com.lumora.pos.system.controller;

import com.lumora.pos.common.dto.ApiResponse;
import com.lumora.pos.system.dto.InventoryHealthResponse;
import com.lumora.pos.system.service.InventoryHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemHealthController {

    private final InventoryHealthService healthService;

    @GetMapping("/health/inventory")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<InventoryHealthResponse>> checkInventoryHealth() {
        InventoryHealthResponse health = healthService.checkCurrentTenantHealth();
        String message = health.isHealthy() ? "Inventory is healthy" : "Inventory discrepancies detected";

        return ResponseEntity.ok(ApiResponse.<InventoryHealthResponse>builder()
                .success(health.isHealthy())
                .message(message)
                .data(health)
                .build());
    }
}
