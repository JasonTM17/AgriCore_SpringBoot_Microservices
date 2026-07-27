ALTER TABLE harvest_batches
    ADD COLUMN last_outbox_event_id UUID;

CREATE UNIQUE INDEX uk_harvest_batches_outbox_event
    ON harvest_batches (last_outbox_event_id);
