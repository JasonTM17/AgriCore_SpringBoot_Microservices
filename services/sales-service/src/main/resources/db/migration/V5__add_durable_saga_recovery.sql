ALTER TABLE order_sagas ADD COLUMN next_attempt_at TIMESTAMP;
ALTER TABLE order_sagas ADD COLUMN execution_started_at TIMESTAMP;
ALTER TABLE order_sagas ADD COLUMN completed_at TIMESTAMP;

UPDATE order_sagas
SET status = 'RETRY_SCHEDULED',
    next_attempt_at = CURRENT_TIMESTAMP
WHERE status = 'FAILED'
  AND current_step IN ('RESERVATION_OUTCOME_UNKNOWN', 'CONFIRM_INVENTORY', 'COMPENSATION_PENDING');

UPDATE order_sagas
SET status = 'PROCESSING',
    execution_started_at = updated_at
WHERE status = 'RUNNING';

CREATE INDEX idx_order_sagas_recovery
    ON order_sagas (status, next_attempt_at, execution_started_at, updated_at);
