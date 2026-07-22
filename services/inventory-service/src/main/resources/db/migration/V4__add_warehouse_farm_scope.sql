ALTER TABLE warehouses ADD COLUMN farm_id UUID;

CREATE INDEX idx_warehouses_farm ON warehouses (farm_id);
