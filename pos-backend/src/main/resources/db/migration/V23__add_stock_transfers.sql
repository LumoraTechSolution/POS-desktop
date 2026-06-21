-- ============================================================
-- V23: Stock Transfers table for inter-branch inventory movement
-- ============================================================

CREATE TABLE stock_transfers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    source_branch_id      UUID NOT NULL REFERENCES branches(id),
    destination_branch_id UUID NOT NULL REFERENCES branches(id),
    product_id      UUID        NOT NULL REFERENCES products(id),
    quantity        INTEGER     NOT NULL CHECK (quantity > 0),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    notes           TEXT,
    completed_at    TIMESTAMP,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP,
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT      DEFAULT 0,

    CONSTRAINT chk_transfer_different_branches CHECK (source_branch_id != destination_branch_id)
);

-- Performance indexes
CREATE INDEX idx_stock_transfers_tenant ON stock_transfers(tenant_id);
CREATE INDEX idx_stock_transfers_status ON stock_transfers(status);
CREATE INDEX idx_stock_transfers_source ON stock_transfers(source_branch_id);
CREATE INDEX idx_stock_transfers_dest   ON stock_transfers(destination_branch_id);
CREATE INDEX idx_stock_transfers_product ON stock_transfers(product_id);
