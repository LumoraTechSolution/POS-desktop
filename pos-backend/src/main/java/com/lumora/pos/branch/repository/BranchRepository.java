package com.lumora.pos.branch.repository;

import com.lumora.pos.branch.entity.BranchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BranchRepository extends JpaRepository<BranchEntity, UUID> {
    List<BranchEntity> findAllByTenantId(UUID tenantId);

    long countByTenantId(UUID tenantId);

    Optional<BranchEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<BranchEntity> findByIsDefaultTrueAndTenantId(UUID tenantId);
}
