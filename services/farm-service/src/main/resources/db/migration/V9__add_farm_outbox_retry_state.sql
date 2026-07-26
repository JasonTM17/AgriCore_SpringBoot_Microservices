ALTER TABLE outbox_events
    ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE outbox_events
    ADD COLUMN quarantined_at TIMESTAMP WITH TIME ZONE;
