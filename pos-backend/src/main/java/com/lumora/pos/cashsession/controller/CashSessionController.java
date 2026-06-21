package com.lumora.pos.cashsession.controller;

import com.lumora.pos.cashsession.dto.CashSessionDtos.CashSessionResponse;
import com.lumora.pos.cashsession.dto.CashSessionDtos.EndShiftRequest;
import com.lumora.pos.cashsession.dto.CashSessionDtos.StartShiftRequest;
import com.lumora.pos.cashsession.service.CashSessionService;
import com.lumora.pos.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cash-session")
@RequiredArgsConstructor
public class CashSessionController {

    private final CashSessionService cashSessionService;

    @PostMapping("/start")
    @PreAuthorize("hasAnyRole('CASHIER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CashSessionResponse>> startShift(
            @Valid @RequestBody StartShiftRequest request,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(
                cashSessionService.startShift(userId, request),
                "Shift started"));
    }

    @PostMapping("/end")
    @PreAuthorize("hasAnyRole('CASHIER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CashSessionResponse>> endShift(
            @Valid @RequestBody EndShiftRequest request,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(
                cashSessionService.endShift(userId, request),
                "Shift ended"));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('CASHIER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CashSessionResponse>> getActive(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        CashSessionResponse active = cashSessionService.getActiveForUser(userId);
        if (active == null) {
            return ResponseEntity.ok(ApiResponse.success(null, "No active cash session"));
        }
        return ResponseEntity.ok(ApiResponse.success(active, "Active cash session retrieved"));
    }
}
