-- Custom / open sale lines: an item not in the catalog (typed name + price).
-- product_id becomes nullable; item_name holds the typed name for those lines.
ALTER TABLE sale_items ALTER COLUMN product_id DROP NOT NULL;
ALTER TABLE sale_items ADD COLUMN item_name VARCHAR(255);
