ALTER TABLE harvest_batches
    ADD COLUMN started_at TIMESTAMP;

UPDATE harvest_batches
SET started_at = harvested_at
WHERE started_at IS NULL;

ALTER TABLE harvest_batches
    ALTER COLUMN started_at SET NOT NULL;

ALTER TABLE harvest_batches
    ALTER COLUMN gross_weight_kg DROP NOT NULL;

ALTER TABLE harvest_batches
    ALTER COLUMN net_weight_kg DROP NOT NULL;

ALTER TABLE harvest_batches
    ALTER COLUMN quality_grade DROP NOT NULL;

ALTER TABLE harvest_batches
    ALTER COLUMN harvested_at DROP NOT NULL;
