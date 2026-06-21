package com.lumora.pos.superadmin.repository;

import com.lumora.pos.superadmin.entity.SuperAdminAuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface SuperAdminAuditLogRepository extends JpaRepository<SuperAdminAuditLogEntity, UUID> {

    Page<SuperAdminAuditLogEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT a FROM SuperAdminAuditLogEntity a " +
           "WHERE (LOWER(a.action) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "    OR LOWER(a.entityType) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "  AND a.createdAt >= :startDate AND a.createdAt <= :endDate " +
           "ORDER BY a.createdAt DESC")
    Page<SuperAdminAuditLogEntity> searchWithDates(
            @Param("search") String search,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    @Query("SELECT a FROM SuperAdminAuditLogEntity a " +
           "WHERE LOWER(a.action) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "   OR LOWER(a.entityType) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "ORDER BY a.createdAt DESC")
    Page<SuperAdminAuditLogEntity> search(@Param("search") String search, Pageable pageable);
}
