CREATE TABLE crop_cycles (
    id                  UUID PRIMARY KEY,
    code                VARCHAR(64) NOT NULL,
    farm_id             UUID NOT NULL,
    plot_id             UUID NOT NULL,
    crop_id             UUID NOT NULL,
    crop_variety_id     UUID,
    planned_start_date  DATE NOT NULL,
    planned_end_date    DATE,
    actual_start_date   DATE,
    actual_end_date     DATE,
    stage               VARCHAR(40) NOT NULL,
    status              VARCHAR(32) NOT NULL,
    notes               TEXT,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_crop_cycles_code ON crop_cycles (code);
CREATE INDEX idx_crop_cycles_plot ON crop_cycles (plot_id);
CREATE INDEX idx_crop_cycles_farm ON crop_cycles (farm_id);
CREATE INDEX idx_crop_cycles_status ON crop_cycles (status);

CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    VARCHAR(100) NOT NULL,
    event_type      VARCHAR(150) NOT NULL,
    topic           VARCHAR(200) NOT NULL,
    payload         TEXT NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    published_at    TIMESTAMP,
    publish_attempts INT NOT NULL DEFAULT 0,
    last_error      TEXT
);

CREATE INDEX idx_outbox_created_at ON outbox_events (created_at);
