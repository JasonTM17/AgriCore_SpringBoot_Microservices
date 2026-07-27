ALTER TABLE outbox_events ADD COLUMN claim_token UUID;
ALTER TABLE outbox_events ADD COLUMN claim_until TIMESTAMP;

CREATE INDEX idx_outbox_publish_claim
    ON outbox_events (published_at, claim_until, created_at);
