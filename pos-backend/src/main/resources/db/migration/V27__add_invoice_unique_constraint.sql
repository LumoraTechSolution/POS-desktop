-- =====================================================
-- V27: Add unique constraint on invoice numbers
-- =====================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_sales_invoice_tenant'
    ) THEN
        ALTER TABLE sales ADD CONSTRAINT uk_sales_invoice_tenant UNIQUE (tenant_id, invoice_number);
    END IF;
END $$;
