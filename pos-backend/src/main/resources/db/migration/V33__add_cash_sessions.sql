-- Cash drawer session tracking: opening float at shift start, closing count +
-- variance at shift end. Every cashier shift has one cash_session.

CREATE TABLE cash_sessions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    time_record_id UUID NOT NULL UNIQUE REFERENCES time_records(id),
    user_id UUID NOT NULL REFERENCES users(id),
    opening_balance NUMERIC(15, 2) NOT NULL,
    closing_balance NUMERIC(15, 2),
    expected_balance NUMERIC(15, 2),
    variance NUMERIC(15, 2),
    status VARCHAR(20) NOT NULL,
    opened_at TIMESTAMP NOT NULL,
    closed_at TIMESTAMP,
    notes TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_cash_sessions_tenant ON cash_sessions(tenant_id);
CREATE INDEX idx_cash_sessions_user ON cash_sessions(user_id);
CREATE INDEX idx_cash_sessions_status ON cash_sessions(status);

-- Only one OPEN session per user at a time.
CREATE UNIQUE INDEX uk_cash_sessions_user_open
    ON cash_sessions(user_id)
    WHERE status = 'OPEN';

-- Link sales to the cash session they were rung up in (nullable for historical
-- sales that predate this feature).
ALTER TABLE sales ADD COLUMN cash_session_id UUID REFERENCES cash_sessions(id);
CREATE INDEX idx_sales_cash_session ON sales(cash_session_id);
