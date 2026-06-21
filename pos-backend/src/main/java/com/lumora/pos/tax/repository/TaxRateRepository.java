package com.lumora.pos.tax.repository;

import com.lumora.pos.tax.entity.TaxRateEntity;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaxRateRepository extends JpaRepository<TaxRateEntity, UUID> {

    List<TaxRateEntity> findAllByTenantIdOrderByNameAsc(UUID tenantId);

    List<TaxRateEntity> findAllByTenantIdAndIsActiveTrue(UUID tenantId);

    Optional<TaxRateEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    @Cacheable(value = "taxRates", key = "#p0")
    Optional<TaxRateEntity> findByIsDefaultTrueAndTenantId(UUID tenantId);

    boolean existsByNameAndTenantId(String name, UUID tenantId);
}
