UPDATE outbox_events
SET publish_attempts = publish_attempts + 1
WHERE published_at IS NOT NULL;

CREATE INDEX idx_inventory_outbox_delivery_queue
    ON outbox_events (published_at, created_at);
