CREATE TABLE processed_events (
    id            UUID PRIMARY KEY,
    event_id      UUID NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_notification_processed_event
    ON processed_events (event_id, consumer_name);

ALTER TABLE notifications ADD COLUMN source_event_id UUID;

CREATE UNIQUE INDEX uk_notifications_source_event
    ON notifications (source_event_id);
