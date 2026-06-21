-- Tenant contact fields for receipt header (store address + phone). All nullable so
-- existing tenants are unaffected until they fill them in from Settings.

ALTER TABLE tenants ADD COLUMN address_line1 VARCHAR(255);
ALTER TABLE tenants ADD COLUMN address_line2 VARCHAR(255);
ALTER TABLE tenants ADD COLUMN phone VARCHAR(50);
