-- =====================================================
-- V41: Super Admin Refresh Tokens
-- Separate from `refresh_tokens` (tenant-scoped, FK to users
-- + tenants). Lets the super-admin frontend keep an opaque
-- refresh token in sessionStorage and silently rotate the
-- short-lived access token without re-prompting credentials.
-- =====================================================
CREATE TABLE super_admin_refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    token           VARCHAR(36) NOT NULL UNIQUE,
    super_admin_id  UUID NOT NULL REFERENCES super_admins(id) ON DELETE CASCADE,
    expires_at      TIMESTAMP NOT NULL,
    is_revoked      BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sa_refresh_tokens_token ON super_admin_refresh_tokens(token);
CREATE INDEX idx_sa_refresh_tokens_admin ON super_admin_refresh_tokens(super_admin_id);
