ALTER TABLE devices ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_devices_status_activity
    ON devices (status, last_seen_at, created_at);
