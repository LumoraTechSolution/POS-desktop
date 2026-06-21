-- V35: Stock integrity (non-negative CHECK) + sale-level branch attribution.
--
-- Strict policy: stock can never go negative at the DB level. The application
-- layer already rejects oversell via pessimistic-locked branch stock checks
-- (SaleService, InventoryAdjustmentService); this constraint is the last line
-- of defence against direct SQL writes or future code regressions.
--
-- sales.branch_id is added so returns can restore stock to the *originating*
-- branch instead of always falling back to the tenant's default branch (which
-- silently miscredited multi-branch tenants).

-- 1. Defensive preflight: clamp any rogue negative stock to zero so the CHECK
--    constraint can be applied safely. Under strict policy no row should be
--    negative today, but this guards against dev/test data drift.
UPDATE stock_levels SET quantity = 0 WHERE quantity < 0;

-- 2. Hard floor at zero.
ALTER TABLE stock_levels
    ADD CONSTRAINT chk_stock_quantity_nonneg CHECK (quantity >= 0);

-- 3. Sales gain branch attribution. Existing rows remain NULL (legacy fallback
--    is handled in ReturnService.restoreStock with a logged warning).
ALTER TABLE sales ADD COLUMN branch_id UUID REFERENCES branches(id);
CREATE INDEX idx_sales_branch ON sales(branch_id);
