-- Identity Service schema

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(320) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(200) NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until    TIMESTAMP,
    last_login_at   TIMESTAMP,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0
);

-- Email is always stored lowercased by the application
CREATE UNIQUE INDEX uk_users_email ON users (email);

CREATE TABLE roles (
    id          UUID PRIMARY KEY,
    code        VARCHAR(64) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP NOT NULL
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(128) NOT NULL UNIQUE,
    family_id       UUID NOT NULL,
    expires_at      TIMESTAMP NOT NULL,
    revoked_at      TIMESTAMP,
    replaced_by     UUID,
    created_at      TIMESTAMP NOT NULL,
    user_agent      VARCHAR(512),
    ip_address      VARCHAR(64)
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);

CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    VARCHAR(100) NOT NULL,
    event_type      VARCHAR(150) NOT NULL,
    topic           VARCHAR(200) NOT NULL,
    payload         TEXT NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    published_at    TIMESTAMP,
    publish_attempts INT NOT NULL DEFAULT 0,
    last_error      TEXT
);

CREATE INDEX idx_outbox_created_at ON outbox_events (created_at);
CREATE INDEX idx_outbox_published_at ON outbox_events (published_at);

-- Seed roles
INSERT INTO roles (id, code, description, created_at) VALUES
    ('11111111-1111-1111-1111-111111111001', 'SYSTEM_ADMIN', 'System administrator', NOW()),
    ('11111111-1111-1111-1111-111111111002', 'FARM_MANAGER', 'Farm manager', NOW()),
    ('11111111-1111-1111-1111-111111111003', 'AGRONOMIST', 'Agricultural engineer', NOW()),
    ('11111111-1111-1111-1111-111111111004', 'FIELD_WORKER', 'Field worker', NOW()),
    ('11111111-1111-1111-1111-111111111005', 'WAREHOUSE_MANAGER', 'Warehouse manager', NOW()),
    ('11111111-1111-1111-1111-111111111006', 'SALES_STAFF', 'Sales staff', NOW()),
    ('11111111-1111-1111-1111-111111111007', 'AUDITOR', 'Read-only auditor', NOW());
