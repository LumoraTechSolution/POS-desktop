package com.lumora.pos.publicapi.controller;

import com.lumora.pos.common.dto.ApiResponse;
import com.lumora.pos.superadmin.entity.TenantEntity;
import com.lumora.pos.superadmin.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/public/tenants")
@RequiredArgsConstructor
public class PublicTenantController {

    private final TenantRepository tenantRepository;

    /**
     * Resolves a subdomain/workspace slug to its internal UUID.
     * Required for frontend login to correctly route the authentication request.
     */
    @GetMapping("/resolve")
    public ResponseEntity<ApiResponse<Map<String, String>>> resolveDomain(@RequestParam String domain) {
        String cleanDomain = domain.trim();
        TenantEntity tenant = tenantRepository.findByDomainOrSlug(cleanDomain).orElse(null);

        if (tenant == null || !tenant.isActive()) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.<Map<String, String>>builder()
                            .success(false)
                            .message("Workspace / Domain not found or is suspended.")
                            .build()
            );
        }

        return ResponseEntity.ok(ApiResponse.success(
                Map.of(
                    "tenantId", tenant.getId().toString(),
                    "name", tenant.getName()
                ),
                "Workspace resolved successfully"
        ));
    }
}
