-- Prevent duplicate tenant_configurations rows for the same tenant.
-- Without this, concurrent first-logins can race and create multiple rows.
ALTER TABLE tenant_configurations
    ADD CONSTRAINT uq_tenant_config_tenant_id UNIQUE (tenant_id);
