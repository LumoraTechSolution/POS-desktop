-- Tie each cash drawer to the branch it was opened at so a session can't silently
-- span branches (a cashier switching branches mid-shift makes variance unattributable).
ALTER TABLE cash_sessions ADD COLUMN branch_id UUID REFERENCES branches(id);
CREATE INDEX idx_cash_sessions_branch ON cash_sessions(branch_id);

-- Backfill historical sessions to their tenant's default branch.
UPDATE cash_sessions cs
SET branch_id = (
    SELECT b.id FROM branches b
    WHERE b.tenant_id = cs.tenant_id AND b.is_default = TRUE
)
WHERE branch_id IS NULL;
