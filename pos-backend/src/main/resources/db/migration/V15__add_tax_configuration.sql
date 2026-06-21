-- V15: Tax Configuration System
-- Replaces the hardcoded 10% tax with a configurable, category-aware tax system.

-- 1. Create Tax Rates Table
CREATE TABLE tax_rates (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    rate        DECIMAL(5,4) NOT NULL,  -- e.g., 0.1000 = 10%, 0.0500 = 5%
    description VARCHAR(255),
    is_default  BOOLEAN NOT NULL DEFAULT FALSE,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT DEFAULT 0
);

CREATE INDEX idx_tax_rates_tenant ON tax_rates(tenant_id);
CREATE INDEX idx_tax_rates_default ON tax_rates(tenant_id, is_default) WHERE is_default = TRUE;

-- 2. Link Categories to Tax Rates (optional — if not set, falls back to tenant default)
ALTER TABLE categories ADD COLUMN tax_rate_id UUID REFERENCES tax_rates(id);

-- 3. Seed a default "Standard Tax (10%)" for every existing tenant
INSERT INTO tax_rates (tenant_id, name, rate, description, is_default, is_active)
SELECT id, 'Standard Tax', 0.1000, 'Default tax rate (10%)', TRUE, TRUE
FROM tenants;
