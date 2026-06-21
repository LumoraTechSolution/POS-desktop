-- =====================================================
-- V29: Add barcode index for fast POS terminal lookups
-- =====================================================
-- The POS terminal barcode scanner needs to resolve
-- a product by barcode (or SKU) in real-time.
-- Without an index, every scan triggers a full table scan.

-- Composite index for fast barcode lookups scoped by tenant
CREATE INDEX IF NOT EXISTS idx_products_barcode_tenant
    ON products (barcode, tenant_id)
    WHERE barcode IS NOT NULL;

-- Unique constraint: no two products in the same tenant can share a barcode
CREATE UNIQUE INDEX IF NOT EXISTS uk_products_barcode_tenant
    ON products (barcode, tenant_id)
    WHERE barcode IS NOT NULL;
