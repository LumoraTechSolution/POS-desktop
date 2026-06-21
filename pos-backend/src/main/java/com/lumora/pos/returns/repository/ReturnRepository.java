package com.lumora.pos.returns.repository;

import com.lumora.pos.returns.entity.ReturnEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReturnRepository extends JpaRepository<ReturnEntity, UUID> {
    Page<ReturnEntity> findAllByTenantId(UUID tenantId, Pageable pageable);

    List<ReturnEntity> findAllBySaleIdAndTenantIdOrderByCreatedAtDesc(UUID saleId, UUID tenantId);

    Optional<ReturnEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("SELECT COALESCE(SUM(r.refundAmount), 0) FROM ReturnEntity r " +
           "WHERE r.tenantId = :tenantId " +
           "AND r.refundMethod = com.lumora.pos.returns.entity.ReturnEntity.RefundMethod.CASH " +
           "AND r.status = com.lumora.pos.returns.entity.ReturnEntity.ReturnStatus.COMPLETED " +
           "AND r.createdAt BETWEEN :from AND :to")
    BigDecimal sumCashRefundsBetween(@Param("tenantId") UUID tenantId,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);
}
