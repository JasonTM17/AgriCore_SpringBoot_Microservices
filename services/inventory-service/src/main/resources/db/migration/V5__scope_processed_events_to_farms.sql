ALTER TABLE processed_events
    ADD COLUMN farm_id UUID;

ALTER TABLE processed_events
    ADD COLUMN warehouse_id UUID REFERENCES warehouses(id);

CREATE INDEX idx_processed_events_scope
    ON processed_events (farm_id, warehouse_id);
