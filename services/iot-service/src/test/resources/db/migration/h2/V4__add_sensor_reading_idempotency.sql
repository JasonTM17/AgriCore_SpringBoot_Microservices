CREATE TABLE sensor_reading_idempotency (
    reading_id      UUID PRIMARY KEY,
    device_id       UUID NOT NULL REFERENCES devices(id),
    metric_type     VARCHAR(64) NOT NULL,
    metric_value    NUMERIC(14, 4) NOT NULL,
    unit            VARCHAR(16) NOT NULL,
    recorded_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO sensor_reading_idempotency (
    reading_id,
    device_id,
    metric_type,
    metric_value,
    unit,
    recorded_at,
    created_at
)
SELECT
    id,
    device_id,
    metric_type,
    metric_value,
    unit,
    recorded_at,
    created_at
FROM sensor_readings;

ALTER TABLE sensor_readings
    DROP PRIMARY KEY;

ALTER TABLE sensor_readings
    ADD CONSTRAINT sensor_readings_pkey PRIMARY KEY (id, recorded_at);

DROP INDEX idx_sensor_readings_device_time;

CREATE INDEX idx_sensor_readings_device_metric_time
    ON sensor_readings (device_id, metric_type, recorded_at DESC);
