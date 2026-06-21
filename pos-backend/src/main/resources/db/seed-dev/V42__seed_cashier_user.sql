-- V42: Seed a CASHIER user for the demo tenant.
--
-- Why: the E2E suite (e2e/cash-session.spec.ts, fixtures/test-credentials.ts)
-- assumes a cashier-only account exists so it can exercise the terminal
-- "open drawer -> sell -> close drawer" flow. Only an ADMIN was seeded before
-- (V3), so those specs had no cashier to log in as.
--
-- Tenant:  a0000000-0000-0000-0000-000000000001  (demo)
-- Role:    b0000000-0000-0000-0000-000000000003  (CASHIER)
-- Branch:  none required — sales/cash-sessions resolve to the tenant's default
--          branch (BranchRepository.findByIsDefaultTrueAndTenantId), there is
--          no per-user branch assignment.
--
-- Credentials (dev/test only):
--   email:    cashier@demo.lumora.com
--   password: Cashier123!   (bcrypt, factor 12)
--   PIN:      4321          (bcrypt, factor 12)

INSERT INTO users (id, tenant_id, email, password_hash, pin, first_name, last_name, is_active)
VALUES (
    'c0000000-0000-0000-0000-000000000002',
    'a0000000-0000-0000-0000-000000000001',
    'cashier@demo.lumora.com',
    '$2b$12$AvyMg3bExRYbROCPKgo7guVeMa7CgMBfXDqB8n4oKELUVv3PWi74C', -- Cashier123!
    '$2b$12$ieHVvbtkUPX3gycZPfd5DeONJq.EPMDmJtPZhx0UenN/d/5o0Up3u', -- 4321
    'Cashier',
    'Demo',
    TRUE
)
ON CONFLICT (tenant_id, email) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
VALUES (
    'c0000000-0000-0000-0000-000000000002',
    'b0000000-0000-0000-0000-000000000003'
)
ON CONFLICT (user_id, role_id) DO NOTHING;
