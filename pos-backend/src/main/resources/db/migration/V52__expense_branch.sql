-- Optional per-branch attribution for expenses. NULL = company-wide overhead (rent,
-- head-office salaries) that isn't tied to a single branch; a non-null branch_id lets
-- branch-scoped P&L / cash-flow attribute the cost to one location.
ALTER TABLE expenses ADD COLUMN branch_id UUID REFERENCES branches(id);
CREATE INDEX idx_expenses_branch ON expenses(branch_id);

-- No backfill: existing expenses stay unscoped (company overhead) until an admin
-- explicitly tags them to a branch.
