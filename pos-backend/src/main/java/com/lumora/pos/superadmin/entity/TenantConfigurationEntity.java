package com.lumora.pos.superadmin.entity;

import com.lumora.pos.superadmin.enums.PlanTier;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * SaaS subscription governance configuration for a tenant.
 *
 * IMPORTANT: This entity does NOT extend BaseEntity.
 * It is NOT scoped by tenant_id for queries — it IS the cross-tenant
 * governance record that super admins manage.
 *
 * One row per tenant. Controls plan tier, feature flags, and limits.
 * Maps to the `tenant_configurations` table (V24 migration).
 */
@Entity
@Table(name = "tenant_configurations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantConfigurationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Foreign key to the tenants table. One config per tenant. */
    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    /** Subscription plan tier. */
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_tier", nullable = false, length = 50)
    @Builder.Default
    private PlanTier planTier = PlanTier.SMALL_BUSINESS;

    /** Maximum number of branch locations the tenant can create. */
    @Column(name = "max_locations", nullable = false)
    @Builder.Default
    private int maxLocations = 1;

    /** Maximum number of users the tenant can have. */
    @Column(name = "max_users", nullable = false)
    @Builder.Default
    private int maxUsers = 5;

    /** Maximum number of products the tenant can create. */
    @Column(name = "max_products", nullable = false)
    @Builder.Default
    private int maxProducts = 500;

    /**
     * JSONB array of feature tags the tenant is permitted to access.
     * Example value: ["SALES", "INVENTORY", "REPORTS", "CUSTOMERS", "EMPLOYEES"]
     *
     * Uses Hibernate's native @JdbcTypeCode(SqlTypes.JSON) to map PostgreSQL
     * JSONB to a Java List<String> — no additional library required.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features_enabled", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<String> featuresEnabled = List.of(
        "SALES", "INVENTORY", "REPORTS", "CUSTOMERS", "EMPLOYEES"
    );

    /**
     * Tenant suspension toggle.
     * When FALSE, the tenant's users cannot authenticate.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    /** Start date of the current subscription period. */
    @Column(name = "subscription_start", nullable = false)
    @Builder.Default
    private LocalDateTime subscriptionStart = LocalDateTime.now();

    /**
     * End date of the subscription period.
     * NULL = indefinitely active (no expiry).
     */
    @Column(name = "subscription_end")
    private LocalDateTime subscriptionEnd;

    /** Internal notes visible only to super admins. */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /** Returns true if the subscription period has expired. */
    public boolean isSubscriptionExpired() {
        return subscriptionEnd != null && subscriptionEnd.isBefore(LocalDateTime.now());
    }

    /** Returns true if a specific feature tag is enabled for this tenant. */
    public boolean hasFeature(String featureTag) {
        return featuresEnabled != null && featuresEnabled.contains(featureTag);
    }
}
