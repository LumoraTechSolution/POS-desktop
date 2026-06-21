package com.lumora.pos.supplier.repository;

import com.lumora.pos.supplier.entity.SupplierEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<SupplierEntity, UUID> {
    Page<SupplierEntity> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<SupplierEntity> findAllByTenantIdAndNameContainingIgnoreCase(UUID tenantId, String name, Pageable pageable);

    Optional<SupplierEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<SupplierEntity> findByIdInAndTenantId(Collection<UUID> ids, UUID tenantId);
}
