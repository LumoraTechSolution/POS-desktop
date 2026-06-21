package com.lumora.pos.cashsession.repository;

import com.lumora.pos.cashsession.entity.CashSessionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CashSessionRepository extends JpaRepository<CashSessionEntity, UUID> {

    @Query("SELECT cs FROM CashSessionEntity cs " +
           "WHERE cs.userId = :userId AND cs.status = com.lumora.pos.cashsession.entity.CashSessionEntity.Status.OPEN")
    Optional<CashSessionEntity> findActiveByUserId(@Param("userId") UUID userId);

    Optional<CashSessionEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Whether any cash session (open or closed) already references this time
     * record. cash_sessions.time_record_id is UNIQUE, so a new session must never
     * reuse a time record that's already attached to one.
     */
    boolean existsByTimeRecordId(UUID timeRecordId);

    @Query("SELECT cs FROM CashSessionEntity cs " +
           "WHERE cs.tenantId = :tenantId " +
           "AND cs.status = com.lumora.pos.cashsession.entity.CashSessionEntity.Status.CLOSED " +
           "AND cs.closedAt BETWEEN :from AND :to " +
           "ORDER BY cs.closedAt DESC")
    Page<CashSessionEntity> findClosedByTenantIdAndDateRange(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @Query("SELECT cs FROM CashSessionEntity cs " +
           "WHERE cs.tenantId = :tenantId " +
           "AND cs.status = com.lumora.pos.cashsession.entity.CashSessionEntity.Status.CLOSED " +
           "AND cs.closedAt BETWEEN :from AND :to " +
           "AND cs.branch.id IN :branchIds " +
           "ORDER BY cs.closedAt DESC")
    Page<CashSessionEntity> findClosedByTenantIdAndDateRangeAndBranch(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("branchIds") Collection<UUID> branchIds,
            Pageable pageable);
}
