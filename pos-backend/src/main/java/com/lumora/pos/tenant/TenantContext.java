package com.lumora.pos.tenant;

import java.util.UUID;

/**
 * ThreadLocal holder for the current tenant ID.
 */
public class TenantContext {
    private static final ThreadLocal<UUID> currentTenant = new ThreadLocal<>();

    public static void setTenantId(UUID tenantId) {
        currentTenant.set(tenantId);
    }

    public static UUID getTenantId() {
        return currentTenant.get();
    }

    public static void clear() {
        currentTenant.remove();
    }

    // Alias for compatibility with existing code if needed
    public static void setCurrentTenant(UUID tenantId) {
        setTenantId(tenantId);
    }
}
