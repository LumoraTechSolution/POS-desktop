CREATE TABLE inventory_adjustments (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    product_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    adjustment_type VARCHAR(50) NOT NULL,
    quantity INTEGER NOT NULL,
    previous_quantity INTEGER NOT NULL,
    new_quantity INTEGER NOT NULL,
    reason TEXT,
    reference_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT DEFAULT 0,
    CONSTRAINT fk_adjustment_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_adjustment_branch FOREIGN KEY (branch_id) REFERENCES branches(id)
);

CREATE INDEX idx_inv_adj_product ON inventory_adjustments(product_id);
CREATE INDEX idx_inv_adj_branch ON inventory_adjustments(branch_id);
CREATE INDEX idx_inv_adj_tenant ON inventory_adjustments(tenant_id);
CREATE INDEX idx_inv_adj_type ON inventory_adjustments(adjustment_type);
