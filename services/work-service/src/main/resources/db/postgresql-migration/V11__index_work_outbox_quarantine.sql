DROP INDEX CONCURRENTLY IF EXISTS idx_work_outbox_quarantine;

CREATE INDEX CONCURRENTLY idx_work_outbox_quarantine
    ON outbox_events (quarantined_at)
    WHERE quarantined_at IS NOT NULL;
