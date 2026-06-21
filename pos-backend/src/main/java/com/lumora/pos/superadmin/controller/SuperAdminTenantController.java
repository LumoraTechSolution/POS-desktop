package com.lumora.pos.superadmin.controller;

import com.lumora.pos.common.dto.ApiResponse;
import com.lumora.pos.common.dto.PagedResponse;
import com.lumora.pos.superadmin.dto.*;
import com.lumora.pos.superadmin.service.SuperAdminTenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.lumora.pos.user.dto.UserManagementDtos.ResetPasswordRequest;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for Super Admin governance of tenants.
 * Base path: /api/v1/super-admin
 *
 * All endpoints in this controller require the ROLE_SUPERADMIN authority
 * (enforced globally in SecurityConfig). None of these endpoints use standard
 * TenantContext filtering.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/super-admin")
@RequiredArgsConstructor
public class SuperAdminTenantController {

    private final SuperAdminTenantService superAdminTenantService;

    // -----------------------------------------------------------------------
    // Dashboard Stats
    // -----------------------------------------------------------------------

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<PlatformStatsResponse>> getPlatformStats() {
        return ResponseEntity.ok(ApiResponse.success(superAdminTenantService.getPlatformStats()));
    }

    // -----------------------------------------------------------------------
    // Tenant Management (CRUD)
    // -----------------------------------------------------------------------

    @GetMapping("/tenants")
    public ResponseEntity<ApiResponse<PagedResponse<TenantSummaryResponse>>> listTenants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive) {

        Page<TenantSummaryResponse> result = superAdminTenantService.listTenants(
                search, isActive, PageRequest.of(page, size, Sort.by("createdAt").descending()));

        PagedResponse<TenantSummaryResponse> pagedResponse = PagedResponse.<TenantSummaryResponse>builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();

        return ResponseEntity.ok(ApiResponse.success(pagedResponse));
    }

    @GetMapping("/tenants/{id}")
    public ResponseEntity<ApiResponse<TenantDetailResponse>> getTenant(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(superAdminTenantService.getTenantDetail(id)));
    }

    @PostMapping("/tenants")
    public ResponseEntity<ApiResponse<TenantDetailResponse>> createTenant(
            @Valid @RequestBody CreateTenantRequest request) {
        TenantDetailResponse response = superAdminTenantService.createTenant(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Tenant provisioned successfully"));
    }

    @PutMapping("/tenants/{id}/config")
    public ResponseEntity<ApiResponse<TenantDetailResponse>> updateConfiguration(
            @PathVariable UUID id,
            @Valid @RequestBody TenantConfigurationRequest request) {
        TenantDetailResponse response = superAdminTenantService.updateTenantConfiguration(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Tenant configuration updated"));
    }

    // -----------------------------------------------------------------------
    // Tenant Users (visibility + admin-mediated recovery)
    // -----------------------------------------------------------------------

    /** Lists a tenant's users — also the "forgot email" lookup surface. */
    @GetMapping("/tenants/{id}/users")
    public ResponseEntity<ApiResponse<List<TenantUserResponse>>> listTenantUsers(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(superAdminTenantService.listTenantUsers(id)));
    }

    /** Resets a tenant user's password; they must change it on next login. */
    @PostMapping("/tenants/{id}/users/{userId}/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetTenantUserPassword(
            @PathVariable UUID id,
            @PathVariable UUID userId,
            @Valid @RequestBody ResetPasswordRequest request) {
        superAdminTenantService.resetTenantUserPassword(id, userId, request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(null,
                "Password reset. The user must change it on next login."));
    }

    @PatchMapping("/tenants/{id}/suspend")
    public ResponseEntity<ApiResponse<TenantDetailResponse>> suspendTenant(@PathVariable UUID id) {
        TenantDetailResponse response = superAdminTenantService.suspendTenant(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Tenant suspended successfully"));
    }

    @PatchMapping("/tenants/{id}/activate")
    public ResponseEntity<ApiResponse<TenantDetailResponse>> activateTenant(@PathVariable UUID id) {
        TenantDetailResponse response = superAdminTenantService.activateTenant(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Tenant activated successfully"));
    }
}
