-- Grant the new finance features to existing tenants by plan tier (matches the
-- defaults applied to newly-created tenants in SuperAdminTenantService).
-- Each statement is idempotent: it only appends a feature the tenant lacks.

-- EXPENSES + FINANCIAL_REPORTS → MEDIUM_BUSINESS and ENTERPRISE
UPDATE tenant_configurations
SET features_enabled = features_enabled || '["EXPENSES"]'::jsonb
WHERE plan_tier IN ('MEDIUM_BUSINESS', 'ENTERPRISE')
  AND NOT (features_enabled @> '["EXPENSES"]'::jsonb);

UPDATE tenant_configurations
SET features_enabled = features_enabled || '["FINANCIAL_REPORTS"]'::jsonb
WHERE plan_tier IN ('MEDIUM_BUSINESS', 'ENTERPRISE')
  AND NOT (features_enabled @> '["FINANCIAL_REPORTS"]'::jsonb);

-- BUDGETING → ENTERPRISE only
UPDATE tenant_configurations
SET features_enabled = features_enabled || '["BUDGETING"]'::jsonb
WHERE plan_tier = 'ENTERPRISE'
  AND NOT (features_enabled @> '["BUDGETING"]'::jsonb);
