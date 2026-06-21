package com.lumora.pos.audit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity mapping to the existing `audit_log` table.
 * Records are immutable — once written, they must never be updated or deleted.
 * This entity does NOT extend BaseEntity intentionally:
 * - No `updated_at` column (audit logs are write-once)
 * - No `@Version` / optimistic locking (no concurrent updates)
 * - No `updated_by` usage (record is never modified)
 */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_tenant", columnList = "tenant_id"),
        @Index(name = "idx_audit_entity", columnList = "tenant_id, entity_type, entity_id"),
        @Index(name = "idx_audit_user", columnList = "tenant_id, user_id"),
        @Index(name = "idx_audit_created", columnList = "tenant_id, created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    /**
     * The action performed. E.g., CREATE, UPDATE, DELETE, LOGIN, LOGOUT, VOID,
     * STOCK_ADJUST.
     */
    @Column(name = "action", nullable = false, length = 50, updatable = false)
    private String action;

    /**
     * The type of entity affected. E.g., PRODUCT, CATEGORY, BRAND, SALE, USER.
     */
    @Column(name = "entity_type", nullable = false, length = 100, updatable = false)
    private String entityType;

    /**
     * The ID of the affected entity (nullable for actions like LOGIN).
     */
    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    /**
     * JSONB snapshot of the entity state BEFORE the action (for UPDATE/DELETE).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "jsonb", updatable = false)
    private String oldValue;

    /**
     * JSONB snapshot of the entity state AFTER the action (for CREATE/UPDATE).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb", updatable = false)
    private String newValue;

    /**
     * Client IP address captured from the HTTP request.
     */
    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    /**
     * Client User-Agent header captured from the HTTP request.
     */
    @Column(name = "user_agent", length = 500, updatable = false)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by", updatable = false)
    private UUID updatedBy;

    /**
     * Automatically set the creation timestamp before persisting.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
