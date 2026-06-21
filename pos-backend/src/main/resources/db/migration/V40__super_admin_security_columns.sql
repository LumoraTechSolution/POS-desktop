-- =====================================================
-- V40: Super Admin Security Hardening Columns
--   * failed_login_attempts / locked_until — per-account
--     brute-force protection on top of RateLimitFilter (per-IP).
--   * password_change_required — gate full access until the
--     seeded default password is rotated on first login.
--   * last_login_ip / last_login_user_agent — surface session
--     anomalies in the super-admin account view.
-- =====================================================
ALTER TABLE super_admins
    ADD COLUMN IF NOT EXISTS failed_login_attempts   INT       NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_until            TIMESTAMP,
    ADD COLUMN IF NOT EXISTS password_change_required BOOLEAN  NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS last_login_ip           VARCHAR(45),
    ADD COLUMN IF NOT EXISTS last_login_user_agent   VARCHAR(500);
