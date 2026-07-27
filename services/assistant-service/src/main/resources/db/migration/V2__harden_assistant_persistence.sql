ALTER TABLE conversations ADD COLUMN context_type VARCHAR(16);
UPDATE conversations
SET context_type = CASE WHEN farm_id IS NULL THEN 'ENTERPRISE' ELSE 'FARM' END;
ALTER TABLE conversations ALTER COLUMN context_type SET NOT NULL;
ALTER TABLE conversations ADD COLUMN next_message_sequence BIGINT NOT NULL DEFAULT 0;
ALTER TABLE conversations ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE conversations ADD COLUMN purge_after TIMESTAMP;
UPDATE conversations SET archived_at = updated_at
WHERE status = 'ARCHIVED' AND archived_at IS NULL;
UPDATE conversations SET purge_after = TIMESTAMP '9999-12-31 23:59:59'
WHERE status = 'ARCHIVED';
ALTER TABLE conversations ADD CONSTRAINT uk_conversation_owner UNIQUE (id, owner_user_id);
ALTER TABLE conversations ADD CONSTRAINT ck_conversation_context CHECK (
    (context_type = 'ENTERPRISE' AND farm_id IS NULL)
    OR (context_type = 'FARM' AND farm_id IS NOT NULL)
);
ALTER TABLE conversations ADD CONSTRAINT ck_conversation_status CHECK (status IN ('OPEN', 'ARCHIVED'));
ALTER TABLE conversations ADD CONSTRAINT ck_conversation_archive CHECK (
    (status = 'OPEN' AND archived_at IS NULL AND purge_after IS NULL)
    OR (status = 'ARCHIVED' AND archived_at IS NOT NULL AND purge_after IS NOT NULL)
);
CREATE INDEX idx_conversations_owner_status
    ON conversations (owner_user_id, status, updated_at DESC, id);
CREATE INDEX idx_conversations_purge ON conversations (purge_after, id);

ALTER TABLE chat_generations ADD COLUMN request_hash VARCHAR(64);
UPDATE chat_generations SET request_hash = 'legacy-' || CAST(id AS VARCHAR(36));
ALTER TABLE chat_generations ALTER COLUMN request_hash SET NOT NULL;
ALTER TABLE chat_generations ADD COLUMN active_conversation_id UUID;
-- V1 had no durable lease or provider idempotency contract. Active work cannot be
-- resumed safely after deployment, so fail it closed instead of risking a paid retry.
UPDATE chat_generations
SET status = 'FAILED',
    error_code = 'MIGRATION_WORKER_LOST',
    completed_at = COALESCE(completed_at, updated_at)
WHERE status IN ('QUEUED', 'RUNNING', 'CANCEL_REQUESTED');
UPDATE chat_generations
SET active_conversation_id = conversation_id
WHERE status IN ('QUEUED', 'RUNNING', 'CANCEL_REQUESTED');
ALTER TABLE chat_generations ADD COLUMN farm_id UUID;
UPDATE chat_generations generation
SET farm_id = (SELECT conversation.farm_id FROM conversations conversation
               WHERE conversation.id = generation.conversation_id);
ALTER TABLE chat_generations ADD COLUMN role_snapshot TEXT;
UPDATE chat_generations generation
SET role_snapshot = (SELECT conversation.role_snapshot FROM conversations conversation
                     WHERE conversation.id = generation.conversation_id);
ALTER TABLE chat_generations ALTER COLUMN role_snapshot SET NOT NULL;
ALTER TABLE chat_generations ADD COLUMN next_event_sequence BIGINT NOT NULL DEFAULT 0;
UPDATE chat_generations generation
SET next_event_sequence = (
    SELECT COALESCE(MAX(event.sequence_no) + 1, 0)
    FROM generation_events event
    WHERE event.generation_id = generation.id
);
ALTER TABLE chat_generations ADD COLUMN provider VARCHAR(32) NOT NULL DEFAULT 'none';
ALTER TABLE chat_generations ADD COLUMN model VARCHAR(128);
ALTER TABLE chat_generations ADD COLUMN input_tokens BIGINT;
ALTER TABLE chat_generations ADD COLUMN output_tokens BIGINT;
ALTER TABLE chat_generations ADD COLUMN first_token_latency_ms BIGINT;
ALTER TABLE chat_generations ADD COLUMN provider_latency_ms BIGINT;
ALTER TABLE chat_generations ADD COLUMN total_latency_ms BIGINT;
ALTER TABLE chat_generations ADD COLUMN queued_at TIMESTAMP;
UPDATE chat_generations SET queued_at = created_at;
ALTER TABLE chat_generations ALTER COLUMN queued_at SET NOT NULL;
ALTER TABLE chat_generations ADD COLUMN started_at TIMESTAMP;
ALTER TABLE chat_generations ADD COLUMN first_token_at TIMESTAMP;
ALTER TABLE chat_generations ADD COLUMN cancel_requested_at TIMESTAMP;
ALTER TABLE chat_generations ADD COLUMN cancelled_at TIMESTAMP;
ALTER TABLE chat_generations ADD COLUMN lease_token UUID;
ALTER TABLE chat_generations ADD COLUMN lease_expires_at TIMESTAMP;
ALTER TABLE chat_generations ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE chat_generations ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE chat_generations ADD COLUMN purge_after TIMESTAMP;
ALTER TABLE chat_generations ADD CONSTRAINT uk_generation_conversation UNIQUE (id, conversation_id);
ALTER TABLE chat_generations ADD CONSTRAINT uk_generation_active_conversation UNIQUE (active_conversation_id);
ALTER TABLE chat_generations ADD CONSTRAINT fk_generation_conversation_owner
    FOREIGN KEY (conversation_id, owner_user_id)
    REFERENCES conversations(id, owner_user_id) ON DELETE CASCADE;
ALTER TABLE chat_generations ADD CONSTRAINT ck_generation_status CHECK (
    status IN ('QUEUED', 'RUNNING', 'CANCEL_REQUESTED', 'COMPLETED', 'FAILED', 'CANCELLED')
);
ALTER TABLE chat_generations ADD CONSTRAINT ck_generation_active_slot CHECK (
    (status IN ('QUEUED', 'RUNNING', 'CANCEL_REQUESTED')
        AND active_conversation_id IS NOT NULL
        AND active_conversation_id = conversation_id)
    OR (status IN ('COMPLETED', 'FAILED', 'CANCELLED') AND active_conversation_id IS NULL)
);
ALTER TABLE chat_generations ADD CONSTRAINT ck_generation_non_negative_usage CHECK (
    (input_tokens IS NULL OR input_tokens >= 0)
    AND (output_tokens IS NULL OR output_tokens >= 0)
    AND (first_token_latency_ms IS NULL OR first_token_latency_ms >= 0)
    AND (provider_latency_ms IS NULL OR provider_latency_ms >= 0)
    AND (total_latency_ms IS NULL OR total_latency_ms >= 0)
    AND attempt_count >= 0
);
CREATE INDEX idx_generations_queue
    ON chat_generations (status, lease_expires_at, queued_at, id);
CREATE INDEX idx_generations_purge ON chat_generations (purge_after, id);

UPDATE conversation_messages message
SET generation_id = (
    SELECT generation.id
    FROM chat_generations generation
    WHERE generation.user_message_id = message.id
)
WHERE generation_id IS NULL
  AND EXISTS (
      SELECT 1 FROM chat_generations generation
      WHERE generation.user_message_id = message.id
  );
UPDATE conversation_messages message
SET generation_id = (
    SELECT generation.id
    FROM chat_generations generation
    WHERE generation.assistant_message_id = message.id
)
WHERE generation_id IS NULL
  AND EXISTS (
      SELECT 1 FROM chat_generations generation
      WHERE generation.assistant_message_id = message.id
  );
ALTER TABLE conversation_messages ADD COLUMN sequence_no BIGINT;
CREATE TABLE assistant_message_sequence_backfill (
    message_id  UUID PRIMARY KEY,
    sequence_no BIGINT NOT NULL
);
INSERT INTO assistant_message_sequence_backfill (message_id, sequence_no)
SELECT id, ROW_NUMBER() OVER (PARTITION BY conversation_id ORDER BY created_at, id) - 1
FROM conversation_messages;
UPDATE conversation_messages message
SET sequence_no = (
    SELECT backfill.sequence_no
    FROM assistant_message_sequence_backfill backfill
    WHERE backfill.message_id = message.id
);
DROP TABLE assistant_message_sequence_backfill;
ALTER TABLE conversation_messages ALTER COLUMN sequence_no SET NOT NULL;
ALTER TABLE conversation_messages ALTER COLUMN generation_id SET NOT NULL;
ALTER TABLE conversation_messages ADD COLUMN token_count BIGINT;
ALTER TABLE conversation_messages ADD CONSTRAINT uk_message_sequence UNIQUE (conversation_id, sequence_no);
ALTER TABLE conversation_messages ADD CONSTRAINT uk_message_generation_role UNIQUE (generation_id, role);
ALTER TABLE conversation_messages ADD CONSTRAINT fk_message_generation_conversation
    FOREIGN KEY (generation_id, conversation_id)
    REFERENCES chat_generations(id, conversation_id) ON DELETE CASCADE;
ALTER TABLE conversation_messages ADD CONSTRAINT ck_message_role CHECK (role IN ('USER', 'ASSISTANT'));
ALTER TABLE conversation_messages ADD CONSTRAINT ck_message_token_count CHECK (
    token_count IS NULL OR token_count >= 0
);
ALTER TABLE chat_generations DROP COLUMN user_message_id;
ALTER TABLE chat_generations DROP COLUMN assistant_message_id;
ALTER TABLE chat_generations DROP COLUMN error_message;
UPDATE conversations conversation
SET next_message_sequence = (
    SELECT COUNT(*)
    FROM conversation_messages message
    WHERE message.conversation_id = conversation.id
);
CREATE INDEX idx_messages_generation ON conversation_messages (generation_id, sequence_no);

UPDATE generation_events SET event_type = UPPER(event_type);
ALTER TABLE generation_events ADD COLUMN expires_at TIMESTAMP;
ALTER TABLE generation_events ADD CONSTRAINT ck_generation_event_type CHECK (
    event_type IN ('STATUS', 'DELTA', 'COMPLETED', 'ERROR', 'CANCELLED')
);
CREATE INDEX idx_generation_events_expiry ON generation_events (expires_at, generation_id);

ALTER TABLE assistant_audit_events ADD COLUMN actor_subject UUID;
UPDATE assistant_audit_events audit
SET owner_user_id = (
    SELECT conversation.owner_user_id
    FROM conversations conversation
    WHERE conversation.id = audit.conversation_id
)
WHERE owner_user_id IS NULL AND conversation_id IS NOT NULL;
UPDATE assistant_audit_events
SET owner_user_id = CAST('00000000-0000-0000-0000-000000000000' AS UUID)
WHERE owner_user_id IS NULL;
UPDATE assistant_audit_events SET actor_subject = owner_user_id;
ALTER TABLE assistant_audit_events ALTER COLUMN actor_subject SET NOT NULL;
ALTER TABLE assistant_audit_events ALTER COLUMN owner_user_id SET NOT NULL;
ALTER TABLE assistant_audit_events ADD COLUMN farm_id UUID;
UPDATE assistant_audit_events audit
SET farm_id = (SELECT conversation.farm_id FROM conversations conversation
               WHERE conversation.id = audit.conversation_id);
ALTER TABLE assistant_audit_events ADD COLUMN outcome VARCHAR(16) NOT NULL DEFAULT 'SUCCESS';
ALTER TABLE assistant_audit_events ADD COLUMN reason_code VARCHAR(64);
ALTER TABLE assistant_audit_events ADD COLUMN trace_id VARCHAR(128);
ALTER TABLE assistant_audit_events ADD COLUMN correlation_id VARCHAR(128);
ALTER TABLE assistant_audit_events ADD COLUMN metadata VARCHAR(2000);
ALTER TABLE assistant_audit_events ADD COLUMN retain_until TIMESTAMP;
UPDATE assistant_audit_events SET retain_until = TIMESTAMP '9999-12-31 23:59:59';
ALTER TABLE assistant_audit_events ALTER COLUMN retain_until SET NOT NULL;
ALTER TABLE assistant_audit_events DROP COLUMN detail;
ALTER TABLE assistant_audit_events ADD CONSTRAINT ck_assistant_audit_outcome CHECK (
    outcome IN ('SUCCESS', 'DENIED', 'FAILED')
);
CREATE INDEX idx_assistant_audit_owner
    ON assistant_audit_events (owner_user_id, created_at DESC, id);
CREATE INDEX idx_assistant_audit_conversation
    ON assistant_audit_events (conversation_id, created_at DESC, id);
CREATE INDEX idx_assistant_audit_generation
    ON assistant_audit_events (generation_id, created_at DESC, id);
CREATE INDEX idx_assistant_audit_retention
    ON assistant_audit_events (retain_until, id);
