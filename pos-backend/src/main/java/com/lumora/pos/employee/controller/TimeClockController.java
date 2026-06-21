package com.lumora.pos.employee.controller;

import com.lumora.pos.common.dto.ApiResponse;
import com.lumora.pos.employee.dto.TimeRecordResponse;
import com.lumora.pos.employee.service.TimeClockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/time-clock")
@RequiredArgsConstructor
public class TimeClockController {

    private final TimeClockService timeClockService;

    @PostMapping("/clock-in")
    @PreAuthorize("hasAnyRole('CASHIER', 'MANAGER', 'INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<TimeRecordResponse>> clockIn(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        TimeRecordResponse response = timeClockService.clockIn(userId);
        return ResponseEntity.ok(ApiResponse.success(response, "Clocked in successfully"));
    }

    @PostMapping("/clock-out")
    @PreAuthorize("hasAnyRole('CASHIER', 'MANAGER', 'INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<TimeRecordResponse>> clockOut(
            @RequestParam(required = false) String notes,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        TimeRecordResponse response = timeClockService.clockOut(userId, notes);
        return ResponseEntity.ok(ApiResponse.success(response, "Clocked out successfully"));
    }

    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('CASHIER', 'MANAGER', 'INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<TimeRecordResponse>> getStatus(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        TimeRecordResponse response = timeClockService.getStatus(userId);
        if (response == null) {
            return ResponseEntity.ok(ApiResponse.success(null, "Not currently clocked in"));
        }
        return ResponseEntity.ok(ApiResponse.success(response, "Currently clocked in"));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('CASHIER', 'MANAGER', 'INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<Page<TimeRecordResponse>>> getHistory(
            Pageable pageable,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        Page<TimeRecordResponse> history = timeClockService.getUserHistory(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(history, "History fetched successfully"));
    }

    @GetMapping("/all-history")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Page<TimeRecordResponse>>> getAllHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        Page<TimeRecordResponse> history = timeClockService.getAllHistory(from, to, status, search, pageable);
        return ResponseEntity.ok(ApiResponse.success(history, "All history fetched successfully"));
    }
}
