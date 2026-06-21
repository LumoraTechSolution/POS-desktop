package com.lumora.pos.inventory.repository;

import com.lumora.pos.inventory.entity.StockTransferEntity;
import com.lumora.pos.inventory.entity.StockTransferEntity.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransferEntity, UUID> {

    Page<StockTransferEntity> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<StockTransferEntity> findByStatusAndTenantId(TransferStatus status, UUID tenantId, Pageable pageable);

    Optional<StockTransferEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<StockTransferEntity> findBySourceBranchIdOrDestinationBranchIdAndTenantId(
            UUID sourceBranchId, UUID destinationBranchId, UUID tenantId, Pageable pageable);
}
