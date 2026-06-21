package com.lumora.pos.superadmin.enums;

/**
 * Subscription plan tiers for tenant configurations.
 * Controls the default limits and features available to a tenant.
 */
public enum PlanTier {

    /**
     * Single-store retail, cafes, boutiques.
     * Limits: 1 location, 5 users, 500 products.
     * Features: Core POS only.
     */
    SMALL_BUSINESS,

    /**
     * Multi-store retail, growing chains.
     * Limits: 3 locations, 15 users, 5000 products.
     * Features: Core + Purchase Orders, Returns, Tax Config.
     */
    MEDIUM_BUSINESS,

    /**
     * Large chains, franchises.
     * Limits: Effectively unlimited (999 locations, 999 users).
     * Features: All features enabled.
     */
    ENTERPRISE
}
