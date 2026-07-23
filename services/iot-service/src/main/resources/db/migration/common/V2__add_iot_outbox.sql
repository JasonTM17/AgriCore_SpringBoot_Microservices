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

CREATE INDEX idx_iot_outbox_delivery_queue
    ON outbox_events (published_at, created_at);
