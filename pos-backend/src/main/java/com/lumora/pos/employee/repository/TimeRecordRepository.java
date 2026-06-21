package com.lumora.pos.employee.repository;

import com.lumora.pos.employee.entity.TimeRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TimeRecordRepository extends JpaRepository<TimeRecord, UUID> {

    @Query("SELECT t FROM TimeRecord t WHERE t.user.id = :userId AND t.clockOutTime IS NULL ORDER BY t.clockInTime DESC LIMIT 1")
    Optional<TimeRecord> findActiveRecordByUserId(UUID userId);

    Page<TimeRecord> findByUserId(UUID userId, Pageable pageable);

    Page<TimeRecord> findAllByTenantId(UUID tenantId, Pageable pageable);

    /**
     * Filtered, paginated time-record listing for a tenant.
     *
     * Important parameter conventions (enforced by the service layer):
     *   - {@code searchPattern}: pre-built lowercase LIKE pattern (e.g. "%john%"), or
     *     empty string for "no search filter". Never NULL — passing NULL strings
     *     into PostgreSQL LOWER/CONCAT fails with "function lower(bytea) does not exist".
     *   - {@code status}: "ACTIVE", "COMPLETED", or "" (no filter). Never NULL.
     *   - {@code from}/{@code to}: required, non-null. Use sentinel min/max if open-ended.
     *
     * Searches firstName and lastName independently to keep PostgreSQL's planner happy
     * and avoid 3-argument CONCAT entirely.
     */
    @Query(value = """
        SELECT t FROM TimeRecord t JOIN t.user u
        WHERE t.tenantId = :tenantId
        AND t.clockInTime >= :from
        AND t.clockInTime <= :to
        AND (:status = ''
             OR (:status = 'ACTIVE' AND t.clockOutTime IS NULL)
             OR (:status = 'COMPLETED' AND t.clockOutTime IS NOT NULL))
        AND (:searchPattern = ''
             OR LOWER(u.firstName) LIKE :searchPattern
             OR LOWER(u.lastName) LIKE :searchPattern)
        ORDER BY t.clockInTime DESC
        """,
        countQuery = """
        SELECT COUNT(t) FROM TimeRecord t JOIN t.user u
        WHERE t.tenantId = :tenantId
        AND t.clockInTime >= :from
        AND t.clockInTime <= :to
        AND (:status = ''
             OR (:status = 'ACTIVE' AND t.clockOutTime IS NULL)
             OR (:status = 'COMPLETED' AND t.clockOutTime IS NOT NULL))
        AND (:searchPattern = ''
             OR LOWER(u.firstName) LIKE :searchPattern
             OR LOWER(u.lastName) LIKE :searchPattern)
        """)
    Page<TimeRecord> findAllByTenantIdFiltered(
        @Param("tenantId") UUID tenantId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        @Param("status") String status,
        @Param("searchPattern") String searchPattern,
        Pageable pageable);
}
