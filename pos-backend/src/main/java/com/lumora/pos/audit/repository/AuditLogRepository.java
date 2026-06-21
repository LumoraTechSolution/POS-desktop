package com.lumora.pos.audit.repository;

import com.lumora.pos.audit.entity.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for audit log persistence and querying.
 * Provides tenant-scoped queries for viewing audit trails.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

    /**
     * Paginated audit log by tenant — primary query for the admin audit dashboard.
     */
    Page<AuditLogEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    /**
     * All audit entries for a specific entity (e.g., full history of a Product).
     */
    List<AuditLogEntity> findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            UUID tenantId, String entityType, UUID entityId);

    /**
     * All audit entries by a specific user (e.g., "what did this cashier do
     * today?").
     */
    Page<AuditLogEntity> findByTenantIdAndUserIdOrderByCreatedAtDesc(
            UUID tenantId, UUID userId, Pageable pageable);

    /**
     * Audit entries filtered by action type within a date range (e.g., all DELETEs
     * this week).
     */
    Page<AuditLogEntity> findByTenantIdAndActionAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID tenantId, String action, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    /**
     * Time-range query for compliance reporting.
     */
    Page<AuditLogEntity> findByTenantIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID tenantId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    /**
     * Global search without dates
     */
    @Query("SELECT a FROM AuditLogEntity a WHERE " +
           "LOWER(a.action) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(a.entityType) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<AuditLogEntity> searchGlobalAuditLogs(@Param("search") String search, Pageable pageable);

    /**
     * Global search WITH dates
     */
    @Query("SELECT a FROM AuditLogEntity a WHERE " +
           "(LOWER(a.action) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(a.entityType) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND a.createdAt >= :startDate AND a.createdAt <= :endDate")
    Page<AuditLogEntity> searchGlobalWithDates(
            @Param("search") String search,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    /**
     * Global filter supporting optional search, action, and date range.
     *
     * Parameter conventions (enforced by the service layer — same approach as
     * {@code TimeRecordRepository.findAllByTenantIdFiltered}): callers must pass
     * {@code ""} for an absent text filter and sentinel min/max for an open-ended
     * date bound. Never NULL. A NULL bound used only inside a {@code (:param IS NULL
     * OR ...)} guard expands to a positional placeholder with no type anchor, and
     * PostgreSQL rejects the prepared statement with "could not determine data type
     * of parameter".
     */
    @Query("SELECT a FROM AuditLogEntity a WHERE " +
           "(:search = '' OR LOWER(a.action) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "             OR LOWER(a.entityType) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:action = '' OR a.action = :action) " +
           "AND a.createdAt >= :startDate " +
           "AND a.createdAt <= :endDate")
    Page<AuditLogEntity> searchGlobalFiltered(
            @Param("search") String search,
            @Param("action") String action,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    /**
     * Distinct action codes present in audit_log. Drives the action
     * multi-select on the super-admin audit page.
     */
    @Query("SELECT DISTINCT a.action FROM AuditLogEntity a ORDER BY a.action")
    List<String> findDistinctActions();
}
