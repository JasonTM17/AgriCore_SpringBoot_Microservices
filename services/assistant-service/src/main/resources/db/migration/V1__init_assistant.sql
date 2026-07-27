CREATE TABLE conversations (
    id              UUID PRIMARY KEY,
    owner_user_id   UUID NOT NULL,
    title           VARCHAR(200) NOT NULL,
    farm_id         UUID,
    status          VARCHAR(32) NOT NULL,
    role_snapshot   TEXT NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    archived_at     TIMESTAMP
);

CREATE INDEX idx_conversations_owner ON conversations (owner_user_id, updated_at DESC);

CREATE TABLE conversation_messages (
    id              UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role            VARCHAR(32) NOT NULL,
    content         TEXT NOT NULL,
    generation_id   UUID,
    created_at      TIMESTAMP NOT NULL
);

CREATE INDEX idx_messages_conversation ON conversation_messages (conversation_id, created_at);

CREATE TABLE chat_generations (
    id              UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    owner_user_id   UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    user_message_id UUID,
    assistant_message_id UUID,
    error_code      VARCHAR(64),
    error_message   TEXT,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    completed_at    TIMESTAMP,
    CONSTRAINT uk_generation_idempotency UNIQUE (owner_user_id, conversation_id, idempotency_key)
);

CREATE INDEX idx_generations_conversation ON chat_generations (conversation_id, created_at DESC);

CREATE TABLE generation_events (
    id              UUID PRIMARY KEY,
    generation_id   UUID NOT NULL REFERENCES chat_generations(id) ON DELETE CASCADE,
    sequence_no     BIGINT NOT NULL,
    event_type      VARCHAR(32) NOT NULL,
    payload         TEXT NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    CONSTRAINT uk_generation_sequence UNIQUE (generation_id, sequence_no)
);

CREATE TABLE assistant_audit_events (
    id              UUID PRIMARY KEY,
    owner_user_id   UUID,
    conversation_id UUID,
    generation_id   UUID,
    action          VARCHAR(64) NOT NULL,
    detail          TEXT,
    created_at      TIMESTAMP NOT NULL
);

CREATE INDEX idx_assistant_audit_created ON assistant_audit_events (created_at DESC);
