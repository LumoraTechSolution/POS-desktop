package com.lumora.pos.branch.service;

import com.lumora.pos.branch.dto.BranchRequest;
import com.lumora.pos.branch.dto.BranchResponse;
import com.lumora.pos.branch.entity.BranchEntity;
import com.lumora.pos.branch.repository.BranchRepository;
import com.lumora.pos.common.exception.BusinessException;
import com.lumora.pos.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;
    private final BranchAccessGuard branchAccessGuard;
    private final com.lumora.pos.superadmin.repository.TenantConfigurationRepository tenantConfigurationRepository;

    @Transactional(readOnly = true)
    public List<BranchResponse> getAllBranches() {
        UUID tenantId = TenantContext.getTenantId();
        return branchRepository.findAllByTenantId(tenantId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Active branches the current user may operate at. When branch restrictions are off
     * (or the caller is an admin) this is every active branch; otherwise it's the user's
     * assigned set. Drives the POS terminal and the branch pickers so each user only sees
     * the branches they can transact at.
     */
    @Transactional(readOnly = true)
    public List<BranchResponse> getMyBranches() {
        UUID tenantId = TenantContext.getTenantId();
        Set<UUID> accessible = branchAccessGuard.accessibleBranchIds();
        return branchRepository.findAllByTenantId(tenantId).stream()
                .filter(b -> b.isActive() && accessible.contains(b.getId()))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BranchResponse getBranchById(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        return branchRepository.findByIdAndTenantId(id, tenantId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new BusinessException("Branch not found"));
    }

    @Transactional
    public BranchResponse createBranch(BranchRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        long currentCount = branchRepository.countByTenantId(tenantId);
        com.lumora.pos.superadmin.entity.TenantConfigurationEntity config = tenantConfigurationRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant configuration not found"));
        if (currentCount >= config.getMaxLocations()) {
            throw new BusinessException("Subscription limit reached: Maximum locations (" + config.getMaxLocations() + ") allowed.");
        }

        BranchEntity branch = BranchEntity.builder()
                .name(request.getName())
                .address(request.getAddress())
                .phoneNumber(request.getPhoneNumber())
                .isActive(request.isActive())
                .isDefault(false) // Only one default branch allowed, usually the one created during tenant init
                .build();

        branch.setTenantId(tenantId);
        BranchEntity saved = branchRepository.save(branch);
        return mapToResponse(saved);
    }

    @Transactional
    public BranchResponse updateBranch(UUID id, BranchRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        BranchEntity branch = branchRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("Branch not found"));

        branch.setName(request.getName());
        branch.setAddress(request.getAddress());
        branch.setPhoneNumber(request.getPhoneNumber());
        branch.setActive(request.isActive());

        BranchEntity updated = branchRepository.save(branch);
        return mapToResponse(updated);
    }

    @Transactional(readOnly = true)
    public BranchEntity getDefaultBranch() {
        UUID tenantId = TenantContext.getTenantId();
        return branchRepository.findByIsDefaultTrueAndTenantId(tenantId)
                .orElseThrow(() -> new BusinessException("Default branch not found for tenant"));
    }

    private BranchResponse mapToResponse(BranchEntity entity) {
        return BranchResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .address(entity.getAddress())
                .phoneNumber(entity.getPhoneNumber())
                .isActive(entity.isActive())
                .isDefault(entity.isDefault())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
