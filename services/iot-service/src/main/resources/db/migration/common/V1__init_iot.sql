CREATE TABLE devices (
    id              UUID PRIMARY KEY,
    device_code     VARCHAR(64) NOT NULL,
    plot_id         UUID NOT NULL,
    name            VARCHAR(200) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    last_seen_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL
);
CREATE UNIQUE INDEX uk_devices_code ON devices (device_code);

CREATE TABLE sensor_readings (
    id              UUID PRIMARY KEY,
    device_id       UUID NOT NULL REFERENCES devices(id),
    metric_type     VARCHAR(64) NOT NULL,
    metric_value    NUMERIC(14, 4) NOT NULL,
    unit            VARCHAR(16) NOT NULL,
    recorded_at     TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL
);
CREATE INDEX idx_sensor_readings_device_time ON sensor_readings (device_id, recorded_at);

CREATE TABLE threshold_rules (
    id              UUID PRIMARY KEY,
    metric_type     VARCHAR(64) NOT NULL,
    min_value       NUMERIC(14, 4),
    max_value       NUMERIC(14, 4),
    severity        VARCHAR(32) NOT NULL,
    rule_version    INT NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL
);

CREATE TABLE sensor_alerts (
    id              UUID PRIMARY KEY,
    device_id       UUID NOT NULL REFERENCES devices(id),
    metric_type     VARCHAR(64) NOT NULL,
    metric_value    NUMERIC(14, 4) NOT NULL,
    severity        VARCHAR(32) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    rule_version    INT NOT NULL,
    fingerprint     VARCHAR(128) NOT NULL,
    message         VARCHAR(500) NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    last_seen_at    TIMESTAMP NOT NULL,
    resolved_at     TIMESTAMP
);
CREATE INDEX idx_sensor_alerts_fingerprint ON sensor_alerts (fingerprint, status);
CREATE INDEX idx_sensor_alerts_device ON sensor_alerts (device_id);

INSERT INTO threshold_rules (id, metric_type, min_value, max_value, severity, rule_version, active, created_at) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'SOIL_MOISTURE', 20.0, 90.0, 'HIGH', 1, TRUE, NOW()),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'AIR_TEMPERATURE', 5.0, 40.0, 'MEDIUM', 1, TRUE, NOW()),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', 'SOIL_PH', 4.5, 7.5, 'MEDIUM', 1, TRUE, NOW());
