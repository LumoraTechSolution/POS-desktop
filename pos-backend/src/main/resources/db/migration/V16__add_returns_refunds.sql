-- ============================================================
-- V16: Returns & Refunds System
-- ============================================================
-- Adds tables for tracking product returns and refund processing.
-- Supports partial returns, manager approval workflow, and
-- maintains full audit trail for financial accountability.
-- ============================================================

-- ─── RETURNS TABLE ──────────────────────────────────────────
CREATE TABLE returns (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    sale_id         UUID NOT NULL REFERENCES sales(id),
    return_number   VARCHAR(50) NOT NULL,

    -- Refund Details
    reason          TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
        -- PENDING   → Awaiting manager approval (if required)
        -- APPROVED  → Manager approved, ready to process
        -- COMPLETED → Refund processed, stock restored
        -- REJECTED  → Manager rejected the return
    refund_amount   DECIMAL(12,2) NOT NULL DEFAULT 0,
    refund_method   VARCHAR(20) NOT NULL DEFAULT 'ORIGINAL',
        -- ORIGINAL → Refund to original payment method
        -- CASH     → Cash refund
        -- STORE_CREDIT → Credit to customer account

    -- Processing metadata
    processed_by    UUID REFERENCES users(id),
    approved_by     UUID REFERENCES users(id),
    notes           TEXT,

    -- Audit columns
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP,
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0,

    -- Constraints
    CONSTRAINT chk_return_status CHECK (status IN ('PENDING', 'APPROVED', 'COMPLETED', 'REJECTED')),
    CONSTRAINT chk_refund_method CHECK (refund_method IN ('ORIGINAL', 'CASH', 'STORE_CREDIT'))
);

-- ─── RETURN ITEMS TABLE ─────────────────────────────────────
CREATE TABLE return_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    return_id           UUID NOT NULL REFERENCES returns(id) ON DELETE CASCADE,
    sale_item_id        UUID NOT NULL REFERENCES sale_items(id),
    product_id          UUID NOT NULL REFERENCES products(id),

    quantity_returned   DECIMAL(10,2) NOT NULL,
    unit_price          DECIMAL(12,2) NOT NULL,
    refund_amount       DECIMAL(12,2) NOT NULL,

    -- Audit columns
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP,
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT NOT NULL DEFAULT 0,

    -- Constraints
    CONSTRAINT chk_return_quantity_positive CHECK (quantity_returned > 0),
    CONSTRAINT chk_return_refund_positive CHECK (refund_amount >= 0)
);

-- ─── INDEXES ────────────────────────────────────────────────
CREATE INDEX idx_returns_tenant ON returns(tenant_id);
CREATE INDEX idx_returns_sale ON returns(sale_id);
CREATE INDEX idx_returns_status ON returns(tenant_id, status);
CREATE INDEX idx_returns_number ON returns(tenant_id, return_number);
CREATE INDEX idx_return_items_return ON return_items(return_id);
CREATE INDEX idx_return_items_product ON return_items(product_id);
