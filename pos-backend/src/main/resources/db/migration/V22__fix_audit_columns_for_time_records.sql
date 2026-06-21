ALTER TABLE time_records 
    DROP COLUMN created_by,
    DROP COLUMN updated_by;

ALTER TABLE time_records 
    ADD COLUMN created_by UUID,
    ADD COLUMN updated_by UUID;
