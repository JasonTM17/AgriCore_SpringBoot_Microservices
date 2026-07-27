ALTER TABLE harvest_batches ADD COLUMN farm_id UUID;

CREATE INDEX idx_harvest_batches_farm ON harvest_batches (farm_id);

COMMENT ON COLUMN harvest_batches.farm_id IS
    'Authoritative plot farm; null only for records created before farm scoping was introduced.';
