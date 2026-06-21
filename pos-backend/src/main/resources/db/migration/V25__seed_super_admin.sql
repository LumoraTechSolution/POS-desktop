-- =====================================================
-- V25: Seed Super Admin (Late Injection)
-- Inserts the default Super Admin if V24 was executed 
-- before the seed was added to the script.
-- =====================================================

INSERT INTO super_admins (
    id,
    email,
    password_hash,
    first_name,
    last_name,
    is_active
) VALUES (
    'f0000000-0000-0000-0000-000000000001',
    'superadmin@lumora.com',
    '$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p4c2jdMO0oEUDRogBWuURm',
    'Lumora',
    'SuperAdmin',
    TRUE
) ON CONFLICT (email) DO NOTHING;

-- Also ensure the Demo Tenant configuration is seeded
INSERT INTO tenant_configurations (
    id,
    tenant_id,
    plan_tier,
    max_locations,
    max_users,
    max_products,
    features_enabled,
    is_active,
    subscription_start,
    notes
) VALUES (
    'e0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000001',
    'ENTERPRISE',
    999,
    999,
    999999,
    '["SALES","INVENTORY","REPORTS","CUSTOMERS","EMPLOYEES","PURCHASE_ORDERS","STOCK_TRANSFERS","RETURNS","TAX_CONFIG","TIME_CLOCK","ADVANCED_ANALYTICS","API_ACCESS"]',
    TRUE,
    NOW(),
    'Demo tenant — full Enterprise access for development and demonstration.'
) ON CONFLICT (tenant_id) DO NOTHING;
