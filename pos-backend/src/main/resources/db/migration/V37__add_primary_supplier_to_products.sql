-- P2 2.3: Add preferred/primary supplier FK on products.
-- ON DELETE SET NULL: deleting a supplier gracefully clears the FK rather than
-- cascading or blocking the deletion.
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS primary_supplier_id UUID
        REFERENCES suppliers(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_products_primary_supplier
    ON products(primary_supplier_id);
