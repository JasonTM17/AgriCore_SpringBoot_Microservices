DROP INDEX CONCURRENTLY IF EXISTS idx_sales_outbox_retry_queue;

CREATE INDEX CONCURRENTLY idx_sales_outbox_retry_queue
    ON outbox_events (created_at, next_attempt_at)
    WHERE published_at IS NULL AND quarantined_at IS NULL;
