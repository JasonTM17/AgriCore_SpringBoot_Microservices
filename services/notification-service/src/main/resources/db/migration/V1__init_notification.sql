CREATE TABLE notifications (
    id              UUID PRIMARY KEY,
    channel         VARCHAR(32) NOT NULL,
    recipient       VARCHAR(320) NOT NULL,
    subject         VARCHAR(300) NOT NULL,
    body            TEXT NOT NULL,
    status          VARCHAR(32) NOT NULL,
    correlation_id  VARCHAR(100),
    error_message   TEXT,
    created_at      TIMESTAMP NOT NULL,
    sent_at         TIMESTAMP
);
CREATE INDEX idx_notifications_status ON notifications (status);
