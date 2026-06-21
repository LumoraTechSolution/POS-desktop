-- Budget feature removed (deemed confusing). Drop its tables and revoke the
-- BUDGETING feature flag. V46/V47 remain as applied history; this forward
-- migration undoes their budget-specific effects.

DROP TABLE IF EXISTS budget_lines;
DROP TABLE IF EXISTS budgets;

UPDATE tenant_configurations
SET features_enabled = features_enabled - 'BUDGETING'
WHERE features_enabled @> '["BUDGETING"]'::jsonb;
