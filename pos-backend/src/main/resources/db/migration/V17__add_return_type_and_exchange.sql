-- Add return_type column to distinguish between refund, exchange, and damaged writeoff
ALTER TABLE returns ADD COLUMN return_type VARCHAR(20) NOT NULL DEFAULT 'REFUND';

-- Add exchange_sale_id to link exchange returns to their replacement sale
ALTER TABLE returns ADD COLUMN exchange_sale_id UUID;

-- Add foreign key constraint for exchange_sale_id
ALTER TABLE returns ADD CONSTRAINT fk_returns_exchange_sale
    FOREIGN KEY (exchange_sale_id) REFERENCES sales(id);

-- Add index for quick lookup of exchange-linked returns
CREATE INDEX idx_returns_exchange_sale ON returns(exchange_sale_id) WHERE exchange_sale_id IS NOT NULL;
