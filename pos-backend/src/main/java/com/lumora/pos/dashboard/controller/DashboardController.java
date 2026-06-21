package com.lumora.pos.dashboard.controller;

import com.lumora.pos.common.dto.ApiResponse;
import com.lumora.pos.dashboard.dto.DashboardResponse;
import com.lumora.pos.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for dashboard analytics.
 * Provides a single endpoint that returns all metrics needed
 * for the overview dashboard page.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getDashboardData(),
                "Dashboard data fetched successfully"));
    }
}
