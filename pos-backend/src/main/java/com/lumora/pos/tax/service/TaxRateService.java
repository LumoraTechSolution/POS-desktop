package com.lumora.pos.tax.service;

import com.lumora.pos.common.exception.BusinessException;
import com.lumora.pos.inventory.entity.ProductEntity;
import com.lumora.pos.tax.dto.TaxRateRequest;
import com.lumora.pos.tax.dto.TaxRateResponse;
import com.lumora.pos.tax.entity.TaxRateEntity;
import com.lumora.pos.tax.repository.TaxRateRepository;
import com.lumora.pos.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaxRateService {

    private final TaxRateRepository taxRateRepository;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    // ─── CRUD ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TaxRateResponse> getAllTaxRates() {
        UUID tenantId = TenantContext.getTenantId();
        return taxRateRepository.findAllByTenantIdOrderByNameAsc(tenantId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaxRateResponse> getActiveTaxRates() {
        UUID tenantId = TenantContext.getTenantId();
        return taxRateRepository.findAllByTenantIdAndIsActiveTrue(tenantId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaxRateResponse getTaxRateById(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        return taxRateRepository.findByIdAndTenantId(id, tenantId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new BusinessException("Tax rate not found"));
    }

    @Transactional
    @CacheEvict(value = "taxRates", key = "T(com.lumora.pos.tenant.TenantContext).getTenantId()")
    public TaxRateResponse createTaxRate(TaxRateRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        if (taxRateRepository.existsByNameAndTenantId(request.getName(), tenantId)) {
            throw new BusinessException("A tax rate with name '" + request.getName() + "' already exists");
        }

        // If this is being set as default, unset the current default
        if (request.isDefault()) {
            unsetCurrentDefault(tenantId);
        }

        TaxRateEntity entity = TaxRateEntity.builder()
                .name(request.getName())
                .rate(percentToDecimal(request.getRate()))
                .description(request.getDescription())
                .isDefault(request.isDefault())
                .isActive(request.isActive())
                .build();
        entity.setTenantId(tenantId);

        TaxRateEntity saved = taxRateRepository.save(entity);
        return mapToResponse(saved);
    }

    @Transactional
    @CacheEvict(value = "taxRates", key = "T(com.lumora.pos.tenant.TenantContext).getTenantId()")
    public TaxRateResponse updateTaxRate(UUID id, TaxRateRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        TaxRateEntity entity = taxRateRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("Tax rate not found"));

        // If setting as new default, unset existing default first
        if (request.isDefault() && !entity.isDefault()) {
            unsetCurrentDefault(tenantId);
        }

        entity.setName(request.getName());
        entity.setRate(percentToDecimal(request.getRate()));
        entity.setDescription(request.getDescription());
        entity.setDefault(request.isDefault());
        entity.setActive(request.isActive());

        TaxRateEntity updated = taxRateRepository.save(entity);
        return mapToResponse(updated);
    }

    @Transactional
    @CacheEvict(value = "taxRates", key = "T(com.lumora.pos.tenant.TenantContext).getTenantId()")
    public void deleteTaxRate(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        TaxRateEntity entity = taxRateRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("Tax rate not found"));

        if (entity.isDefault()) {
            throw new BusinessException("Cannot delete the default tax rate. Set another rate as default first.");
        }

        taxRateRepository.delete(entity);
    }

    // ─── TAX RESOLUTION LOGIC ───────────────────────────────

    /**
     * Resolves the applicable tax rate for a product using the chain:
     * Product → Category → category.taxRate → rate
     * Fallback → Tenant default tax rate
     * Fallback → 0 (tax-exempt)
     */
    @Transactional(readOnly = true)
    public BigDecimal getApplicableRate(ProductEntity product) {
        // 1. Check if product's category has an assigned tax rate
        if (product.getCategory() != null && product.getCategory().getTaxRate() != null) {
            TaxRateEntity categoryTax = product.getCategory().getTaxRate();
            if (categoryTax.isActive()) {
                return categoryTax.getRate();
            }
        }

        // 2. Fallback to tenant's default tax rate
        return getDefaultRate(product.getTenantId());
    }

    /**
     * Tenant's default tax rate (active), or 0 if none. Used for line items with
     * no product/category to resolve from — e.g. custom/open sale lines.
     */
    @Transactional(readOnly = true)
    public BigDecimal getDefaultRate(UUID tenantId) {
        return taxRateRepository.findByIsDefaultTrueAndTenantId(tenantId)
                .filter(TaxRateEntity::isActive)
                .map(TaxRateEntity::getRate)
                .orElse(BigDecimal.ZERO);
    }

    // ─── HELPERS ────────────────────────────────────────────

    private void unsetCurrentDefault(UUID tenantId) {
        taxRateRepository.findByIsDefaultTrueAndTenantId(tenantId)
                .ifPresent(existing -> {
                    existing.setDefault(false);
                    taxRateRepository.save(existing);
                });
    }

    /**
     * Convert human-entered percentage (e.g. 10) to decimal (e.g. 0.1000).
     */
    private BigDecimal percentToDecimal(BigDecimal percent) {
        return percent.divide(HUNDRED, 4, RoundingMode.HALF_UP);
    }

    /**
     * Convert stored decimal (e.g. 0.1000) to human-friendly percentage (e.g.
     * 10.00).
     */
    private BigDecimal decimalToPercent(BigDecimal decimal) {
        return decimal.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    }

    private TaxRateResponse mapToResponse(TaxRateEntity entity) {
        return TaxRateResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .rate(entity.getRate())
                .ratePercent(decimalToPercent(entity.getRate()))
                .description(entity.getDescription())
                .isDefault(entity.isDefault())
                .isActive(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
