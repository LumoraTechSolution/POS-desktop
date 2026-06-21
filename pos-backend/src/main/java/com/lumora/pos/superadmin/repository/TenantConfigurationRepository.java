package com.lumora.pos.superadmin.repository;

import com.lumora.pos.superadmin.entity.TenantConfigurationEntity;
import com.lumora.pos.superadmin.enums.PlanTier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for TenantConfigurationEntity.
 * Queries the `tenant_configurations` table.
 * No tenant scoping — super admin module operates across all tenants.
 */
@Repository
public interface TenantConfigurationRepository extends JpaRepository<TenantConfigurationEntity, UUID> {

    @Cacheable(value = "tenantConfigs", key = "#p0")
    Optional<TenantConfigurationEntity> findByTenantId(UUID tenantId);

    boolean existsByTenantId(UUID tenantId);

    List<TenantConfigurationEntity> findByPlanTier(PlanTier planTier);

    List<TenantConfigurationEntity> findByIsActive(boolean isActive);

    long countByPlanTier(PlanTier planTier);

    long countByIsActive(boolean isActive);
}
