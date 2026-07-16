CREATE TABLE harvest_batches (
    id                  UUID PRIMARY KEY,
    code                VARCHAR(64) NOT NULL,
    crop_cycle_id       UUID NOT NULL,
    plot_id             UUID NOT NULL,
    warehouse_id        UUID NOT NULL,
    product_code        VARCHAR(64) NOT NULL,
    gross_weight_kg     NUMERIC(14, 3) NOT NULL,
    net_weight_kg       NUMERIC(14, 3) NOT NULL,
    quality_grade       VARCHAR(32) NOT NULL,
    status              VARCHAR(32) NOT NULL,
    harvested_at        TIMESTAMP NOT NULL,
    notes               TEXT,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_harvest_batches_code ON harvest_batches (code);
CREATE INDEX idx_harvest_batches_cycle ON harvest_batches (crop_cycle_id);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(150) NOT NULL,
    topic VARCHAR(200) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    publish_attempts INT NOT NULL DEFAULT 0,
    last_error TEXT
);
