package com.lumora.pos.sales.controller;

import com.lumora.pos.audit.AuditAction;
import com.lumora.pos.audit.service.AuditService;
import com.lumora.pos.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Handles operations specifically related to physical POS terminal hardware.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/terminal/hardware")
@RequiredArgsConstructor
public class HardwareActionController {

    private final AuditService auditService;

    /**
     * Logs the intention to open the cash drawer outside of a normal sale flow.
     * This endpoints MUST be hit before the frontend locally triggers the ESC/POS kick-code.
     */
    @PostMapping("/open-drawer")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    public ResponseEntity<ApiResponse<Void>> logOpenDrawer() {
        // Log the action to the secure audit log (passing null as entity ID since it's a general action)
        // If an entityType is required, we use "CASH_DRAWER".
        // A temporary deterministic UUID is passed for entityId to satisfy DB schema constraints if required,
        // although usually entityId is nullable in our schema for non-entity actions (like login).
        
        auditService.log(AuditAction.OPEN_DRAWER, "CASH_DRAWER", null);
        
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Drawer open action audited successfully")
                .build());
    }
}
