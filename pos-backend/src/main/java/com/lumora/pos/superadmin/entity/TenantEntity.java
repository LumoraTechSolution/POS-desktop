package com.lumora.pos.superadmin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read-only JPA entity mapping the existing `tenants` table.
 *
 * This entity is used by the Super Admin module to list and manage tenants.
 * The `tenants` table was created in V1 migration and is the root table
 * for multi-tenancy. This entity gives us JPA access to read tenant records.
 *
 * NOTE: Tenant creation for onboarding involves inserting into this table
 * along with creating a TenantConfigurationEntity.
 */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(unique = true, length = 255)
    private String domain;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(length = 50)
    private String phone;

    /**
     * Tenant-level settings (arbitrary JSONB). Currently used for
     * custom branding, receipt config, etc.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String settings;

    /**
     * Receipt logo as a self-contained data URI (e.g. "data:image/png;base64,…").
     * Embedded rather than stored as a file/URL so it survives restarts, rides along
     * in DB backups, and prints without a network fetch.
     */
    @Column(name = "logo_data_uri", columnDefinition = "text")
    private String logoDataUri;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
