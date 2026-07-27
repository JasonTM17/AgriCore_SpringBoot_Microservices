UPDATE outbox_events
SET publish_attempts = publish_attempts + 1
WHERE published_at IS NOT NULL;

CREATE INDEX idx_harvest_outbox_delivery_queue
    ON outbox_events (published_at, created_at);

CREATE UNIQUE INDEX uk_harvest_outbox_aggregate_event
    ON outbox_events (aggregate_type, aggregate_id, event_type);
