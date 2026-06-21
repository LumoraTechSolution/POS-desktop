ALTER TABLE suppliers 
ADD COLUMN created_by UUID REFERENCES users(id),
ADD COLUMN updated_by UUID REFERENCES users(id);

ALTER TABLE purchase_orders 
ADD COLUMN updated_by UUID REFERENCES users(id);

ALTER TABLE purchase_order_items 
ADD COLUMN created_by UUID REFERENCES users(id),
ADD COLUMN updated_by UUID REFERENCES users(id);
