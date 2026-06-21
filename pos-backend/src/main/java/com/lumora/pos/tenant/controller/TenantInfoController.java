package com.lumora.pos.tenant.controller;

import com.lumora.pos.common.dto.ApiResponse;
import com.lumora.pos.tenant.dto.TenantInfoDtos.LogoUploadResponse;
import com.lumora.pos.tenant.dto.TenantInfoDtos.TenantInfoResponse;
import com.lumora.pos.tenant.dto.TenantInfoDtos.TenantInfoUpdateRequest;
import com.lumora.pos.tenant.service.LogoEncodingService;
import com.lumora.pos.tenant.service.TenantInfoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/tenant/info")
@RequiredArgsConstructor
public class TenantInfoController {

    private final TenantInfoService tenantInfoService;
    private final LogoEncodingService logoEncodingService;

    /**
     * Read — any authenticated user in the tenant can fetch the business info
     * (cashiers need the name/address/phone to render receipts).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<TenantInfoResponse>> getCurrentTenantInfo() {
        return ResponseEntity.ok(ApiResponse.success(
                tenantInfoService.getCurrentTenantInfo(),
                "Tenant info retrieved successfully"));
    }

    /**
     * Update — restricted to ADMIN. Managers/cashiers shouldn't change the
     * store name/address/phone on their own.
     */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TenantInfoResponse>> updateCurrentTenantInfo(
            @Valid @RequestBody TenantInfoUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                tenantInfoService.updateCurrentTenantInfo(request),
                "Tenant info updated successfully"));
    }

    /**
     * Validates a logo image and returns it as a data URI to embed in the tenant
     * settings form. It is only persisted when the caller subsequently saves via
     * PUT /tenant/info.
     */
    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LogoUploadResponse>> uploadLogo(
            @RequestParam("file") MultipartFile file) {
        String dataUri = logoEncodingService.toDataUri(file);
        LogoUploadResponse body = LogoUploadResponse.builder().logoUrl(dataUri).build();
        return ResponseEntity.ok(ApiResponse.success(body, "Logo uploaded successfully"));
    }
}
