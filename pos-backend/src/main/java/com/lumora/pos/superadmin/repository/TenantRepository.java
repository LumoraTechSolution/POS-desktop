package com.lumora.pos.superadmin.repository;

import com.lumora.pos.superadmin.entity.TenantEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for TenantEntity.
 * Provides cross-tenant access to the root `tenants` table
 * for the Super Admin module only.
 */
@Repository
public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {

    @Query("SELECT t FROM TenantEntity t WHERE LOWER(t.domain) = LOWER(:domain) OR LOWER(t.domain) = LOWER(CONCAT(:domain, '.lumora.com'))")
    Optional<TenantEntity> findByDomainOrSlug(@Param("domain") String domain);

    boolean existsByDomain(String domain);

    java.util.List<TenantEntity> findByIsActiveTrue();

    @Query("SELECT t FROM TenantEntity t WHERE " +
           "(LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(t.domain) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:isActive IS NULL OR t.isActive = :isActive)")
    Page<TenantEntity> searchTenants(@Param("search") String search, @Param("isActive") Boolean isActive, Pageable pageable);
}
