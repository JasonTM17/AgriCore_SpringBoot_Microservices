-- Consumer-side idempotency: a redelivered eventId must not create a second notification.
CREATE TABLE processed_events (
    event_id        VARCHAR(100) NOT NULL,
    consumer_name   VARCHAR(100) NOT NULL,
    processed_at    TIMESTAMP NOT NULL,
    PRIMARY KEY (event_id, consumer_name)
);
