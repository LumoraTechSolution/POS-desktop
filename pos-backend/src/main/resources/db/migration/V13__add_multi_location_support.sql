-- V13: Multi-Location Support (Branches and Stock Levels)

-- 1. Create Branches Table
CREATE TABLE branches (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    address     VARCHAR(255),
    phone_number VARCHAR(20),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    is_default  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT DEFAULT 0
);

CREATE INDEX idx_branches_tenant ON branches(tenant_id);

-- 2. Create Stock Levels Table (Per Product Per Branch)
CREATE TABLE stock_levels (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    product_id  UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    branch_id   UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    quantity    INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT DEFAULT 0,
    CONSTRAINT uk_stock_levels_product_branch UNIQUE (product_id, branch_id)
);

CREATE INDEX idx_stock_levels_tenant ON stock_levels(tenant_id);
CREATE INDEX idx_stock_levels_product ON stock_levels(product_id);
CREATE INDEX idx_stock_levels_branch ON stock_levels(branch_id);

-- 3. Create a 'Default Store' for every existing tenant
INSERT INTO branches (tenant_id, name, is_default, is_active)
SELECT id, 'Default Store', TRUE, TRUE FROM tenants;

-- 4. Migrate existing stock quantities to the new stock_levels table
-- We map each product to the default branch of its tenant
INSERT INTO stock_levels (tenant_id, product_id, branch_id, quantity)
SELECT p.tenant_id, p.id, b.id, p.stock_quantity
FROM products p
JOIN branches b ON p.tenant_id = b.tenant_id
WHERE b.is_default = TRUE;

-- 5. Note: We keep products.stock_quantity for now to avoid breaking existing code immediately,
-- but future updates should favor the stock_levels table.
