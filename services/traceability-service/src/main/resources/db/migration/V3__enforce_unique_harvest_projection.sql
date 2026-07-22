DROP INDEX idx_traceability_harvest;

CREATE UNIQUE INDEX uk_traceability_harvest_batch
    ON traceability_batches (harvest_batch_id);
