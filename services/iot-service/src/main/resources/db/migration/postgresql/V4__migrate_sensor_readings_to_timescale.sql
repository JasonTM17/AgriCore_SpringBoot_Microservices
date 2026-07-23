DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_extension
        WHERE extname = 'timescaledb'
    ) THEN
        RAISE EXCEPTION
            'TimescaleDB extension is required in the IoT database before running migration V4';
    END IF;
END
$$;

CREATE TABLE sensor_reading_idempotency (
    reading_id      UUID PRIMARY KEY,
    device_id       UUID NOT NULL REFERENCES devices(id),
    metric_type     VARCHAR(64) NOT NULL,
    metric_value    NUMERIC(14, 4) NOT NULL,
    unit            VARCHAR(16) NOT NULL,
    recorded_at     TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL
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
    recorded_at AT TIME ZONE 'UTC',
    created_at AT TIME ZONE 'UTC'
FROM sensor_readings;

ALTER TABLE sensor_readings
    DROP CONSTRAINT sensor_readings_pkey;

ALTER TABLE sensor_readings
    ADD CONSTRAINT sensor_readings_pkey PRIMARY KEY (id, recorded_at);

SELECT create_hypertable(
    'sensor_readings',
    by_range('recorded_at', INTERVAL '7 days'),
    migrate_data => TRUE,
    create_default_indexes => FALSE
);

DROP INDEX idx_sensor_readings_device_time;

CREATE INDEX idx_sensor_readings_device_metric_time
    ON sensor_readings (device_id, metric_type, recorded_at DESC);
