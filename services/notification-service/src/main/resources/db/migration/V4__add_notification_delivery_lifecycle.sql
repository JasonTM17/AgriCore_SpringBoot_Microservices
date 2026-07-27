ALTER TABLE notifications ADD COLUMN source_event_type VARCHAR(150);
ALTER TABLE notifications ADD COLUMN idempotency_key VARCHAR(100);
ALTER TABLE notifications ADD COLUMN error_code VARCHAR(100);
ALTER TABLE notifications ADD COLUMN failure_retryable BOOLEAN;
ALTER TABLE notifications ADD COLUMN failed_at TIMESTAMP;
ALTER TABLE notifications ADD COLUMN delivery_started_at TIMESTAMP;
ALTER TABLE notifications ADD COLUMN delivery_claim_id UUID;
ALTER TABLE notifications ADD COLUMN delivery_attempts INT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uk_notifications_idempotency_key
    ON notifications (idempotency_key);

CREATE INDEX ix_notifications_delivery_recovery
    ON notifications (status, delivery_started_at, created_at);

ALTER TABLE notifications ADD CONSTRAINT ck_notifications_delivery_status
    CHECK (status IN ('REQUESTED', 'DELIVERING', 'SENT', 'FAILED'));
