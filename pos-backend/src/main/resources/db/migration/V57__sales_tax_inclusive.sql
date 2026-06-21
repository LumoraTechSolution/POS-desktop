-- Records whether a sale's line prices were VAT-inclusive (tax extracted from the
-- price) or exclusive (tax added on top) at the moment it was rung up. Captured
-- per sale so a reprinted tax invoice stays faithful even if the tenant later
-- flips its pricing mode. Every sale that existed before this column was added
-- was computed tax-exclusive, hence DEFAULT FALSE.
ALTER TABLE sales ADD COLUMN tax_inclusive BOOLEAN NOT NULL DEFAULT FALSE;
