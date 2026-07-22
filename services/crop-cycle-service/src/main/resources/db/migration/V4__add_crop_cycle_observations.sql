CREATE TABLE crop_cycle_observations (
    id               UUID PRIMARY KEY,
    crop_cycle_id    UUID NOT NULL,
    category         VARCHAR(32) NOT NULL,
    severity         VARCHAR(16) NOT NULL,
    title            VARCHAR(120) NOT NULL,
    details          TEXT NOT NULL,
    observed_at      TIMESTAMP NOT NULL,
    recorded_by      VARCHAR(255) NOT NULL,
    created_at       TIMESTAMP NOT NULL,
    CONSTRAINT fk_crop_cycle_observations_cycle
        FOREIGN KEY (crop_cycle_id) REFERENCES crop_cycles (id),
    CONSTRAINT ck_crop_cycle_observation_category CHECK (
        category IN ('GENERAL', 'GROWTH', 'SOIL', 'IRRIGATION', 'WEATHER',
                     'NUTRITION', 'PEST', 'DISEASE', 'DAMAGE', 'HARVEST_READINESS')
    ),
    CONSTRAINT ck_crop_cycle_observation_severity CHECK (
        severity IN ('INFO', 'ATTENTION', 'CRITICAL')
    )
);

CREATE INDEX idx_crop_cycle_observations_cycle_time
    ON crop_cycle_observations (crop_cycle_id, observed_at, created_at, id);
