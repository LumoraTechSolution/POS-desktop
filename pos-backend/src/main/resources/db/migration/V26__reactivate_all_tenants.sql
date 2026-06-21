-- Maintenance: Reactivate all tenants
-- Resetting status flags for both the root tenant and saas configuration layers.

UPDATE tenant_configurations
SET is_active = TRUE 
WHERE is_active = FALSE;

UPDATE tenants 
SET is_active = TRUE 
WHERE is_active = FALSE;
