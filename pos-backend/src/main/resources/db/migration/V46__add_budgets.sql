-- Budgets: a total allocation for a period (the "500k") with optional per-category
-- lines. Actuals are computed live from expenses; nothing is stored as "spent".

CREATE TABLE budgets (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_amount NUMERIC(14, 2) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_budgets_tenant ON budgets(tenant_id);

CREATE TABLE budget_lines (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    budget_id UUID NOT NULL REFERENCES budgets(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES expense_categories(id),
    allocated_amount NUMERIC(14, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_budget_lines_budget ON budget_lines(budget_id);
