-- Operating-expense tracking (non-inventory spend: rent, payroll, utilities, …).
-- Inventory spend stays in purchase_orders; expenses here feed P&L (as opex) and
-- cash flow (as outflow). Default categories are seeded lazily per tenant in code.

CREATE TABLE expense_categories (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_expense_categories_tenant ON expense_categories(tenant_id);

CREATE TABLE expenses (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    category_id UUID NOT NULL REFERENCES expense_categories(id),
    amount NUMERIC(12, 2) NOT NULL,
    expense_date DATE NOT NULL,
    payee VARCHAR(255),
    payment_method VARCHAR(30),
    reference VARCHAR(100),
    notes TEXT,
    recurring BOOLEAN NOT NULL DEFAULT FALSE,
    recurring_interval VARCHAR(20),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_expenses_tenant_date ON expenses(tenant_id, expense_date);
CREATE INDEX idx_expenses_tenant_category ON expenses(tenant_id, category_id);
