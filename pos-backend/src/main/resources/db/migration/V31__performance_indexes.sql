-- Partial indexes for soft-delete: exclude deleted rows from regular tenant queries
CREATE INDEX IF NOT EXISTS idx_sales_active
    ON sales (tenant_id, created_at DESC) WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_returns_active
    ON returns (tenant_id, created_at DESC) WHERE is_deleted = false;

-- Composite index for sales reporting (tenant + delete flag + date)
CREATE INDEX IF NOT EXISTS idx_sales_tenant_status_date
    ON sales (tenant_id, is_deleted, created_at DESC);

-- Missing index on products.brand_id (avoids full scan on brand-filtered queries)
CREATE INDEX IF NOT EXISTS idx_products_brand
    ON products (tenant_id, brand_id);

-- Composite index for return status queries
CREATE INDEX IF NOT EXISTS idx_returns_status
    ON returns (tenant_id, status, created_at DESC);

-- Audit query index
CREATE INDEX IF NOT EXISTS idx_inventory_adj_created_by
    ON inventory_adjustments (tenant_id, created_by);

-- Tenant-scoped refresh token lookup (prevents cross-tenant token reuse)
CREATE UNIQUE INDEX IF NOT EXISTS idx_refresh_tokens_tenant_token
    ON refresh_tokens (tenant_id, token);
