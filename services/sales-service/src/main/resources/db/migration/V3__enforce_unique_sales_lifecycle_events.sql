CREATE UNIQUE INDEX uk_sales_outbox_lifecycle_event
    ON outbox_events (aggregate_type, aggregate_id, event_type);
