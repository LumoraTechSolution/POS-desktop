package com.lumora.pos.inventory.repository;

import com.lumora.pos.inventory.entity.BrandEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BrandRepository extends JpaRepository<BrandEntity, UUID> {

    List<BrandEntity> findAllByTenantId(UUID tenantId);

    Optional<BrandEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("SELECT b FROM BrandEntity b WHERE b.tenantId = :tenantId AND LOWER(b.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<BrandEntity> searchByName(@Param("tenantId") UUID tenantId, @Param("search") String search);

    Optional<BrandEntity> findByNameAndTenantId(String name, UUID tenantId);
}
