-- V11: Performance Indices for Reporting
-- Adds composite indices to speed up date-range queries used in
-- daily/weekly/monthly sales reporting and audit log queries.

-- Sales table: Used by SaleService.getDailySummary() which queries
-- findByTenantIdAndCreatedAtBetween(tenantId, startOfDay, endOfDay)
CREATE INDEX IF NOT EXISTS idx_sales_tenant_created
    ON sales (tenant_id, created_at DESC);

-- Sales table: Quick lookup by payment method for payment breakdown reports
CREATE INDEX IF NOT EXISTS idx_sales_tenant_payment
    ON sales (tenant_id, payment_method);

-- Sales table: Customer purchase history (used for CRM/loyalty in Phase 2.2)
CREATE INDEX IF NOT EXISTS idx_sales_tenant_customer
    ON sales (tenant_id, customer_id)
    WHERE customer_id IS NOT NULL;

-- Sale items: Product-level sales analysis (e.g., "top-selling products" report)
CREATE INDEX IF NOT EXISTS idx_sale_items_product
    ON sale_items (tenant_id, product_id);
