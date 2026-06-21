package com.lumora.pos.returns.controller;

import com.lumora.pos.common.dto.ApiResponse;
import com.lumora.pos.returns.dto.ReturnRequest;
import com.lumora.pos.returns.dto.ReturnResponse;
import com.lumora.pos.returns.service.ReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/returns")
@RequiredArgsConstructor
public class ReturnController {

    private final ReturnService returnService;

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'CASHIER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ReturnResponse>> createReturn(@Valid @RequestBody ReturnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(returnService.createReturn(request), "Return created successfully"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'CASHIER')")
    public ResponseEntity<ApiResponse<Page<ReturnResponse>>> getAllReturns(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(returnService.getAllReturns(pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'CASHIER')")
    public ResponseEntity<ApiResponse<ReturnResponse>> getReturnById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(returnService.getReturnById(id)));
    }

    @GetMapping("/sale/{saleId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'CASHIER')")
    public ResponseEntity<ApiResponse<List<ReturnResponse>>> getReturnsBySaleId(@PathVariable UUID saleId) {
        return ResponseEntity.ok(ApiResponse.success(returnService.getReturnsBySaleId(saleId)));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')") // Cashiers cannot approve
    public ResponseEntity<ApiResponse<ReturnResponse>> approveReturn(
            @PathVariable UUID id,
            @RequestParam boolean approve) {
        String message = approve ? "Return approved successfully" : "Return rejected";
        return ResponseEntity.ok(ApiResponse.success(returnService.approveReturn(id, approve), message));
    }

    @PostMapping("/exchange")
    @PreAuthorize("hasAnyRole('MANAGER', 'CASHIER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ReturnResponse>> processExchange(@Valid @RequestBody ReturnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(returnService.processExchange(request), "Exchange processed successfully"));
    }
}
