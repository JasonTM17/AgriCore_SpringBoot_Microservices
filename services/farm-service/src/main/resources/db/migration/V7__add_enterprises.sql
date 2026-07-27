CREATE TABLE enterprises (
    id              UUID PRIMARY KEY,
    code            VARCHAR(64) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    legal_name      VARCHAR(250),
    tax_code        VARCHAR(64),
    address         VARCHAR(500),
    province        VARCHAR(120),
    status          VARCHAR(32) NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    created_by      VARCHAR(255) NOT NULL,
    updated_by      VARCHAR(255) NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_enterprises_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX uk_enterprises_code ON enterprises (code);
CREATE UNIQUE INDEX uk_enterprises_tax_code ON enterprises (tax_code);
CREATE INDEX idx_enterprises_status_name ON enterprises (status, name);
CREATE INDEX idx_enterprises_province ON enterprises (province);
