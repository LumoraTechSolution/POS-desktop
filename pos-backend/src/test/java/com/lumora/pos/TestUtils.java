package com.lumora.pos;

import com.lumora.pos.tenant.TenantContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.UUID;

/**
 * Shared test utilities for setting up common test context
 * (tenant, security principal) without boilerplate.
 */
public final class TestUtils {

    // Fixed UUIDs for predictable test data
    public static final UUID TEST_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID TEST_ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private TestUtils() {
        // Utility class — no instantiation
    }

    /**
     * Sets the TenantContext for the current thread.
     * Must be cleared after each test via {@link #clearContext()}.
     */
    public static void setTenant(UUID tenantId) {
        TenantContext.setTenantId(tenantId);
    }

    /**
     * Sets the SecurityContext with a given user UUID as principal.
     * This is required for @CreatedBy / @LastModifiedBy JPA auditing.
     */
    public static void setSecurityUser(UUID userId) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userId, null,
                Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /**
     * Sets up both TenantContext and SecurityContext with default test values.
     * Call this in @BeforeEach for most service tests.
     */
    public static void setupDefaultContext() {
        setTenant(TEST_TENANT_ID);
        setSecurityUser(TEST_USER_ID);
    }

    /**
     * Clears both TenantContext and SecurityContext.
     * Call this in @AfterEach to prevent context leaking between tests.
     */
    public static void clearContext() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }
}
