-- Existing farms are intentionally left unassigned because the legacy schema has no owner.
-- A SYSTEM_ADMIN must grant their initial membership; failing closed avoids cross-farm exposure.
CREATE TABLE farm_memberships (
    id          UUID PRIMARY KEY,
    farm_id     UUID NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    subject     VARCHAR(255) NOT NULL,
    granted_by  VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_farm_memberships_farm_subject
    ON farm_memberships (farm_id, subject);
CREATE INDEX idx_farm_memberships_subject_farm
    ON farm_memberships (subject, farm_id);
