-- =====================================================
-- V39: Super Admin Audit Log
-- Dedicated table for platform-level (super-admin) events.
-- Kept separate from `audit_log` because:
--   1. audit_log.tenant_id is NOT NULL with FK to tenants — super
--      admin events have no owning tenant.
--   2. audit_log.user_id has FK to users — super admin ids live
--      in super_admins, a different table.
--   3. Architectural invariant: super-admin auth and data must
--      remain entirely separate from tenant auth/data.
-- =====================================================
CREATE TABLE super_admin_audit_log (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    super_admin_id  UUID REFERENCES super_admins(id) ON DELETE SET NULL,
    -- Optional: set when the action targets a specific tenant
    -- (suspend, activate, config update, provision). NULL for
    -- non-tenant-scoped events (login, logout).
    tenant_id       UUID REFERENCES tenants(id) ON DELETE SET NULL,
    action          VARCHAR(50)  NOT NULL,
    entity_type     VARCHAR(100) NOT NULL,
    entity_id       UUID,
    old_value       JSONB,
    new_value       JSONB,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(500),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sa_audit_super_admin ON super_admin_audit_log(super_admin_id);
CREATE INDEX idx_sa_audit_tenant      ON super_admin_audit_log(tenant_id);
CREATE INDEX idx_sa_audit_action      ON super_admin_audit_log(action, created_at DESC);
CREATE INDEX idx_sa_audit_created     ON super_admin_audit_log(created_at DESC);
