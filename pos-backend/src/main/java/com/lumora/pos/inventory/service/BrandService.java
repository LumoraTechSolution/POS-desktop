package com.lumora.pos.inventory.service;

import com.lumora.pos.audit.service.AuditService;
import com.lumora.pos.common.exception.BusinessException;
import com.lumora.pos.inventory.dto.BrandRequest;
import com.lumora.pos.inventory.dto.BrandResponse;
import com.lumora.pos.inventory.entity.BrandEntity;
import com.lumora.pos.inventory.repository.BrandRepository;
import com.lumora.pos.inventory.repository.ProductRepository;
import com.lumora.pos.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<BrandResponse> getAllBrands(String search) {
        UUID tenantId = TenantContext.getTenantId();
        List<BrandEntity> brands = (search != null && !search.trim().isEmpty())
                ? brandRepository.searchByName(tenantId, search.trim())
                : brandRepository.findAllByTenantId(tenantId);
        return brands.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BrandResponse getBrandById(UUID id) {
        return brandRepository.findByIdAndTenantId(id, TenantContext.getTenantId())
                .map(this::mapToResponse)
                .orElseThrow(() -> new BusinessException("Brand not found"));
    }

    @Transactional
    public BrandResponse createBrand(BrandRequest request) {
        BrandEntity brand = BrandEntity.builder()
                .name(request.getName())
                .description(request.getDescription())
                .website(request.getWebsite())
                .build();

        brand.setTenantId(TenantContext.getTenantId());
        BrandResponse response = mapToResponse(brandRepository.save(brand));

        // Audit: Record new brand creation
        auditService.logCreate("BRAND", response.getId(), response);

        return response;
    }

    @Transactional
    public BrandResponse updateBrand(UUID id, BrandRequest request) {
        BrandEntity brand = brandRepository.findByIdAndTenantId(id, TenantContext.getTenantId())
                .orElseThrow(() -> new BusinessException("Brand not found"));

        // Audit: Capture state BEFORE mutations
        BrandResponse oldState = mapToResponse(brand);

        brand.setName(request.getName());
        brand.setDescription(request.getDescription());
        brand.setWebsite(request.getWebsite());

        BrandResponse newState = mapToResponse(brandRepository.save(brand));

        // Audit: Record brand update with before/after
        auditService.logUpdate("BRAND", id, oldState, newState);

        return newState;
    }

    @Transactional
    public void deleteBrand(UUID id) {
        BrandEntity brand = brandRepository.findByIdAndTenantId(id, TenantContext.getTenantId())
                .orElseThrow(() -> new BusinessException("Brand not found"));

        // Audit: Capture snapshot BEFORE deletion
        BrandResponse deletedState = mapToResponse(brand);

        // Deletion Guard: Prevent deletion if products are linked
        UUID tenantId = TenantContext.getTenantId();
        long linkedProducts = productRepository.countByBrandIdAndTenantId(id, tenantId);
        if (linkedProducts > 0) {
            throw new BusinessException(
                    "Cannot delete brand '" + brand.getName() + "': " +
                            linkedProducts + " product(s) are still linked to it. " +
                            "Please reassign or delete those products first.");
        }

        brandRepository.delete(brand);

        auditService.logDelete("BRAND", id, deletedState);
    }

    private BrandResponse mapToResponse(BrandEntity brand) {
        return BrandResponse.builder()
                .id(brand.getId())
                .name(brand.getName())
                .description(brand.getDescription())
                .website(brand.getWebsite())
                .createdAt(brand.getCreatedAt())
                .build();
    }
}
