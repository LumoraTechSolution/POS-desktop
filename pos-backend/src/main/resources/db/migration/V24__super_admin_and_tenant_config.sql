-- =====================================================
-- V24: Super Admin & Tenant Configuration
-- Lumora POS System — SaaS Governance Layer
-- =====================================================

-- =====================================================
-- SUPER_ADMINS — Platform-level operator accounts
-- These are NOT scoped by tenant. They govern the platform itself.
-- =====================================================
CREATE TABLE super_admins (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_super_admins_email ON super_admins(email);

-- =====================================================
-- TENANT_CONFIGURATIONS — SaaS subscription governance
-- One row per tenant. Controls plan tier, feature access, and limits.
-- =====================================================
CREATE TABLE tenant_configurations (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id           UUID NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,

    -- Subscription Plan
    plan_tier           VARCHAR(50) NOT NULL DEFAULT 'SMALL_BUSINESS',
                                                -- SMALL_BUSINESS | MEDIUM_BUSINESS | ENTERPRISE

    -- Resource Limits (enforced by backend interceptors)
    max_locations       INTEGER NOT NULL DEFAULT 1,
    max_users           INTEGER NOT NULL DEFAULT 5,
    max_products        INTEGER NOT NULL DEFAULT 500,

    -- Feature Access Control (JSON array of enabled feature tags)
    -- Example: '["SALES","INVENTORY","REPORTS","CUSTOMERS","EMPLOYEES"]'
    features_enabled    JSONB NOT NULL DEFAULT '["SALES","INVENTORY","REPORTS","CUSTOMERS","EMPLOYEES"]',

    -- Tenant Status (is_active = FALSE suspends the tenant — they cannot log in)
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,

    -- Subscription Dates
    subscription_start  TIMESTAMP NOT NULL DEFAULT NOW(),
    subscription_end    TIMESTAMP,               -- NULL = no expiry (active subscription)

    -- Internal Notes (visible only to super admins)
    notes               TEXT,

    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_tenant_config_tenant  ON tenant_configurations(tenant_id);
CREATE INDEX idx_tenant_config_active  ON tenant_configurations(is_active);
CREATE INDEX idx_tenant_config_tier    ON tenant_configurations(plan_tier);

-- =====================================================
-- SEED: Default Tenant Configuration for Demo Business
-- a0000000-0000-0000-0000-000000000001 = Demo Business (from V1)
-- =====================================================
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
);

-- =====================================================
-- SEED: Initial Super Admin Account
-- Email:    superadmin@lumora.com
-- NOTE: Password is set via seed migration. Change after first login.
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
);
