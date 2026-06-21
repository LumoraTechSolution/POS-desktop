-- User → branch assignment for branch-level access control (feature: BRANCH_RESTRICTIONS).
-- Adds a primary branch per user plus a many-to-many for staff who legitimately work
-- several branches (relief managers, regional inventory managers). No behaviour change
-- until the feature flag is enabled and enforcement lands in later phases.

-- Primary branch a user works at. Nullable for backfill safety and for back-office /
-- super-admin users who have no branch context.
ALTER TABLE users ADD COLUMN primary_branch_id UUID REFERENCES branches(id);
CREATE INDEX idx_users_primary_branch ON users(primary_branch_id);

-- Branches a user is allowed to operate at. The primary also appears here; the
-- "can the user act at branch X" check always reads from this table.
CREATE TABLE user_branches (
    user_id    UUID NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    branch_id  UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, branch_id)
);
CREATE INDEX idx_user_branches_user   ON user_branches(user_id);
CREATE INDEX idx_user_branches_branch ON user_branches(branch_id);

-- Backfill: assign every existing user to their tenant's default branch. Single-branch
-- tenants are immediately correct; multi-branch admins narrow assignments per user once
-- they enable the feature. Tenants with no default branch leave the user unassigned.
INSERT INTO user_branches (user_id, branch_id, is_primary)
SELECT u.id, b.id, TRUE
FROM users u
JOIN branches b ON b.tenant_id = u.tenant_id AND b.is_default = TRUE;

UPDATE users u
SET primary_branch_id = (
    SELECT b.id FROM branches b
    WHERE b.tenant_id = u.tenant_id AND b.is_default = TRUE
);
