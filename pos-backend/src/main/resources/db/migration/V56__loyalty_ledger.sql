-- Loyalty points ledger + sale-level redemption columns.
--
-- Until now loyalty was a single running integer on customers.loyalty_points with
-- no history and no way to spend points. This adds:
--   * loyalty_transactions — an append-only ledger of every earn/redeem/adjust,
--     with the running balance captured per row so the customer's "Loyalty
--     Activity" view is auditable.
--   * sales.loyalty_points_redeemed / loyalty_discount_amount — what (if any)
--     points were spent on a given sale and the bill reduction they bought.
--
-- Earn/redeem rates live in the tenant settings JSONB (no schema change needed).

CREATE TABLE loyalty_transactions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    customer_id UUID NOT NULL REFERENCES customers(id),
    -- Null for manual adjustments; set for points earned/redeemed on a sale.
    sale_id UUID REFERENCES sales(id),
    -- EARN (+points), REDEEM (-points), ADJUST (+/- manual correction).
    type VARCHAR(20) NOT NULL,
    -- Signed: positive adds to the balance, negative subtracts.
    points INT NOT NULL,
    -- The customer's resulting points balance after this entry was applied.
    balance_after INT NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_loyalty_tx_tenant_customer ON loyalty_transactions(tenant_id, customer_id, created_at DESC);

ALTER TABLE sales ADD COLUMN loyalty_points_redeemed INT NOT NULL DEFAULT 0;
ALTER TABLE sales ADD COLUMN loyalty_discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;
