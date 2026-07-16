CREATE TABLE traceability_batches (
    id                  UUID PRIMARY KEY,
    traceability_code   VARCHAR(64) NOT NULL,
    harvest_batch_id    UUID NOT NULL,
    crop_cycle_id       UUID,
    plot_id             UUID,
    farm_name           VARCHAR(200),
    plot_code           VARCHAR(64),
    product_name        VARCHAR(200) NOT NULL,
    variety_name        VARCHAR(200),
    planting_date       DATE,
    harvest_date        DATE NOT NULL,
    quality_grade       VARCHAR(32),
    net_weight_kg       NUMERIC(14, 3),
    care_summary        TEXT,
    qr_url              VARCHAR(500) NOT NULL,
    created_at          TIMESTAMP NOT NULL
);
CREATE UNIQUE INDEX uk_traceability_code ON traceability_batches (traceability_code);
CREATE INDEX idx_traceability_harvest ON traceability_batches (harvest_batch_id);

CREATE TABLE processed_events (
    event_id        VARCHAR(100) NOT NULL,
    consumer_name   VARCHAR(100) NOT NULL,
    processed_at    TIMESTAMP NOT NULL,
    PRIMARY KEY (event_id, consumer_name)
);
