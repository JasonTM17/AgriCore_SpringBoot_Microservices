CREATE TABLE crop_cycle_stage_history (
    id               UUID PRIMARY KEY,
    crop_cycle_id    UUID NOT NULL,
    previous_stage   VARCHAR(40),
    stage            VARCHAR(40) NOT NULL,
    status           VARCHAR(32) NOT NULL,
    notes            TEXT,
    changed_by       VARCHAR(255) NOT NULL,
    changed_at       TIMESTAMP NOT NULL,
    cycle_version    BIGINT NOT NULL,
    CONSTRAINT fk_crop_cycle_stage_history_cycle
        FOREIGN KEY (crop_cycle_id) REFERENCES crop_cycles (id)
);

-- Existing cycles cannot reconstruct prior transitions, so preserve their current state as an
-- explicit migration baseline. Reusing the cycle UUID is deterministic and safe in this table.
INSERT INTO crop_cycle_stage_history (
    id,
    crop_cycle_id,
    previous_stage,
    stage,
    status,
    notes,
    changed_by,
    changed_at,
    cycle_version
)
SELECT
    id,
    id,
    NULL,
    stage,
    status,
    notes,
    'migration',
    updated_at,
    version
FROM crop_cycles;

CREATE INDEX idx_crop_cycle_stage_history_cycle_time
    ON crop_cycle_stage_history (crop_cycle_id, cycle_version, changed_at, id);
