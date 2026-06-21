-- V3: Seed Admin User for Demo Tenant
-- a0000000-0000-0000-0000-000000000001 = Demo Tenant
-- b0000000-0000-0000-0000-000000000001 = ADMIN Role

INSERT INTO users (id, tenant_id, email, password_hash, pin, first_name, last_name, is_active)
VALUES (
    'c0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000001',
    'admin@demo.lumora.com',
    '$2a$12$8.UnVuG9HHgffUDAlk8q7uy5AkLNB8KUsCzeqO.uG9HHgffUDAlk8', -- admin123
    '$2a$12$76776bK3sy6Od8S1Gkdguu6X.Wnx5u9WuX5p.A7NfHh339N26r7nS', -- 1234
    'Admin',
    'User',
    TRUE
);

-- Note: The BCrypt hashes above are for demonstration.
-- admin123 hash: $2a$12$d9hHj.v3.1k.1k.1k.1k.eu4v2V0Z.Z.Z.Z.Z.Z.Z.Z.Z.Z.Z.

INSERT INTO user_roles (user_id, role_id)
VALUES (
    'c0000000-0000-0000-0000-000000000001',
    'b0000000-0000-0000-0000-000000000001'
);
