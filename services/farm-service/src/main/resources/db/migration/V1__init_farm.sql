CREATE TABLE farms (
    id              UUID PRIMARY KEY,
    code            VARCHAR(64) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    address         VARCHAR(500),
    province        VARCHAR(120),
    total_area_ha   NUMERIC(12, 4),
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    status          VARCHAR(32) NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_farms_code ON farms (code);

CREATE TABLE plots (
    id              UUID PRIMARY KEY,
    farm_id         UUID NOT NULL REFERENCES farms(id),
    area_id         UUID,
    code            VARCHAR(64) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    area_in_hectares NUMERIC(12, 4) NOT NULL,
    soil_type       VARCHAR(100),
    status          VARCHAR(32) NOT NULL,
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_plots_farm_code ON plots (farm_id, code);
CREATE INDEX idx_plots_farm_id ON plots (farm_id);
CREATE INDEX idx_plots_status ON plots (status);

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
