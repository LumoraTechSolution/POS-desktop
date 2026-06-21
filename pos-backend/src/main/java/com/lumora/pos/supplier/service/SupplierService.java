package com.lumora.pos.supplier.service;

import com.lumora.pos.common.exception.BusinessException;
import com.lumora.pos.supplier.dto.SupplierRequest;
import com.lumora.pos.supplier.dto.SupplierResponse;
import com.lumora.pos.supplier.entity.SupplierEntity;
import com.lumora.pos.supplier.repository.SupplierRepository;
import com.lumora.pos.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;

    @Transactional
    public SupplierResponse createSupplier(SupplierRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        SupplierEntity entity = SupplierEntity.builder()
                .name(request.getName())
                .contactPerson(request.getContactPerson())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .isActive(request.isActive())
                .build();
        entity.setTenantId(tenantId);

        return mapToResponse(supplierRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponse> getAllSuppliers(String search, Pageable pageable) {
        UUID tenantId = TenantContext.getTenantId();

        if (search != null && !search.isBlank()) {
            return supplierRepository.findAllByTenantIdAndNameContainingIgnoreCase(tenantId, search.trim(), pageable)
                    .map(this::mapToResponse);
        }

        return supplierRepository.findAllByTenantId(tenantId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public SupplierResponse getSupplierById(UUID id) {
        return supplierRepository.findByIdAndTenantId(id, TenantContext.getTenantId())
                .map(this::mapToResponse)
                .orElseThrow(() -> new BusinessException("Supplier not found"));
    }

    @Transactional
    public SupplierResponse updateSupplier(UUID id, SupplierRequest request) {
        SupplierEntity entity = supplierRepository.findByIdAndTenantId(id, TenantContext.getTenantId())
                .orElseThrow(() -> new BusinessException("Supplier not found"));

        entity.setName(request.getName());
        entity.setContactPerson(request.getContactPerson());
        entity.setEmail(request.getEmail());
        entity.setPhone(request.getPhone());
        entity.setAddress(request.getAddress());
        entity.setActive(request.isActive());

        return mapToResponse(supplierRepository.save(entity));
    }

    @Transactional
    public void deleteSupplier(UUID id) {
        SupplierEntity entity = supplierRepository.findByIdAndTenantId(id, TenantContext.getTenantId())
                .orElseThrow(() -> new BusinessException("Supplier not found"));

        // Soft delete or status change is preferable if they are linked to POs
        entity.setActive(false);
        supplierRepository.save(entity);
    }

    @Transactional
    public SupplierResponse toggleStatus(UUID id) {
        SupplierEntity entity = supplierRepository.findByIdAndTenantId(id, TenantContext.getTenantId())
                .orElseThrow(() -> new BusinessException("Supplier not found"));

        entity.setActive(!entity.isActive());
        return mapToResponse(supplierRepository.save(entity));
    }

    /**
     * Sets the active flag for the given suppliers in one transaction. Tenant-scoped
     * (foreign/unknown ids are skipped). Returns the number actually updated.
     */
    @Transactional
    public int bulkSetStatus(List<UUID> ids, boolean active) {
        List<SupplierEntity> found = supplierRepository.findByIdInAndTenantId(ids, TenantContext.getTenantId());
        found.forEach(s -> s.setActive(active));
        supplierRepository.saveAll(found);
        return found.size();
    }

    private SupplierResponse mapToResponse(SupplierEntity entity) {
        return SupplierResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .contactPerson(entity.getContactPerson())
                .email(entity.getEmail())
                .phone(entity.getPhone())
                .address(entity.getAddress())
                .isActive(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
