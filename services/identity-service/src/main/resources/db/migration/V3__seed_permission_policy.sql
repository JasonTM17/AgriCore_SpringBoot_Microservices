ALTER TABLE roles
    ADD COLUMN permission_policy_version BIGINT NOT NULL DEFAULT 1;

ALTER TABLE roles
    ADD CONSTRAINT ck_roles_permission_policy_version
        CHECK (permission_policy_version >= 1);

ALTER TABLE permissions
    ADD COLUMN assignable BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE permissions
    ADD COLUMN catalog_version INTEGER;

ALTER TABLE permissions
    ADD CONSTRAINT ck_permissions_catalog_metadata
        CHECK (
            (assignable = TRUE AND catalog_version IS NOT NULL AND catalog_version >= 1)
            OR (assignable = FALSE AND catalog_version IS NULL)
        );

CREATE TABLE role_permission_policy_audits (
    id                 UUID PRIMARY KEY,
    role_id            UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    policy_version     BIGINT NOT NULL,
    actor_subject      VARCHAR(200) NOT NULL,
    changed_at         TIMESTAMP NOT NULL,
    reason             VARCHAR(500) NOT NULL,
    before_permissions TEXT NOT NULL,
    after_permissions  TEXT NOT NULL,
    CONSTRAINT uk_role_permission_policy_audit_version UNIQUE (role_id, policy_version),
    CONSTRAINT ck_role_permission_policy_audit_version CHECK (policy_version > 1),
    CONSTRAINT ck_role_permission_policy_audit_actor CHECK (CHAR_LENGTH(TRIM(actor_subject)) > 0),
    CONSTRAINT ck_role_permission_policy_audit_reason CHECK (CHAR_LENGTH(TRIM(reason)) > 0)
);

CREATE INDEX idx_role_permission_policy_audits_changed_at
    ON role_permission_policy_audits (changed_at);

-- V2 exposed runtime permission creation before any production enforcement existed.
-- Preserve those rows and grants for rollback/audit, but only migration-owned definitions
-- are assignable or effective after this migration.
CREATE TABLE permission_catalog_seed (
    id          UUID PRIMARY KEY,
    code        VARCHAR(64) NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMP NOT NULL
);

INSERT INTO permission_catalog_seed (id, code, name, description, created_at) VALUES
    ('22222222-2222-2222-2222-222222222001', 'IDENTITY_USER_READ', 'Read identity users', 'View user identity and role assignments.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222002', 'IDENTITY_USER_ADMIN', 'Administer identity users', 'Replace user role assignments and administer accounts.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222003', 'IDENTITY_POLICY_READ', 'Read identity policy', 'View the canonical permission catalog and role grants.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222004', 'IDENTITY_POLICY_ADMIN', 'Administer identity policy', 'Replace versioned role permission grants.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222005', 'FARM_READ', 'Read farms', 'View farms and farm-owned resources within membership scope.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222006', 'FARM_WRITE', 'Write farms', 'Create and update farms and farm-owned resources within membership scope.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222007', 'FARM_ADMIN', 'Administer farms', 'Administer farm memberships and farm structure.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222008', 'CROP_CATALOG_READ', 'Read crop catalog', 'View crops, varieties, and care profiles.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222009', 'CROP_CATALOG_WRITE', 'Write crop catalog', 'Create and update crop care profiles.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222010', 'CROP_CYCLE_READ', 'Read crop cycles', 'View crop cycles within farm scope.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222011', 'CROP_CYCLE_WRITE', 'Write crop cycles', 'Create, update, and cancel crop cycles within farm scope.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222012', 'CROP_CYCLE_USE', 'Operate crop cycles', 'Record authorized crop-cycle activity within farm scope.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222013', 'WORK_READ', 'Read work tasks', 'View farm work tasks within farm scope.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222014', 'WORK_WRITE', 'Write work tasks', 'Create, update, and cancel farm work tasks.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222015', 'WORK_USE', 'Operate work tasks', 'Assign and complete authorized farm work.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222016', 'HARVEST_READ', 'Read harvests', 'View harvest records within farm scope.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222017', 'HARVEST_WRITE', 'Write harvests', 'Complete and repair harvest processing within farm scope.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222018', 'INVENTORY_READ', 'Read inventory', 'View inventory within farm scope.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222019', 'INVENTORY_WRITE', 'Write inventory', 'Administer warehouses, items, and stock movements.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222020', 'INVENTORY_USE', 'Operate inventory', 'Reserve, release, and consume inventory through authorized workflows.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222021', 'SALES_READ', 'Read sales', 'View sales orders within farm scope.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222022', 'SALES_WRITE', 'Write sales', 'Create and update sales orders.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222023', 'SALES_USE', 'Operate sales', 'Advance authorized sales fulfillment workflows.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222024', 'IOT_READ', 'Read IoT data', 'View devices, readings, and alerts within farm scope.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222025', 'IOT_WRITE', 'Write IoT devices', 'Register and administer IoT devices within farm scope.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222026', 'IOT_USE', 'Operate IoT devices', 'Submit authorized IoT readings within farm scope.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222027', 'TRACEABILITY_READ', 'Read traceability', 'View traceability status and projections.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222028', 'TRACEABILITY_WRITE', 'Write traceability', 'Generate and administer traceability projections.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222029', 'TRACEABILITY_USE', 'Operate traceability', 'Acknowledge and operate traceability workflows.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222030', 'NOTIFICATION_READ', 'Read notifications', 'View notification state.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222031', 'NOTIFICATION_ADMIN', 'Administer notifications', 'Request and administer notification delivery.', TIMESTAMP '2026-07-23 00:00:00'),
    ('22222222-2222-2222-2222-222222222032', 'ASSISTANT_USE', 'Use assistant', 'Use the bounded agricultural assistant within authorized farm scope.', TIMESTAMP '2026-07-23 00:00:00');

-- A legacy row may already use a now-canonical code. Promote it in place, replace
-- potentially misleading runtime metadata, and preserve its identifier and grants.
UPDATE permissions
SET name = (
        SELECT seed.name
        FROM permission_catalog_seed seed
        WHERE seed.code = permissions.code
    ),
    description = (
        SELECT seed.description
        FROM permission_catalog_seed seed
        WHERE seed.code = permissions.code
    ),
    assignable = TRUE,
    catalog_version = 1
WHERE EXISTS (
    SELECT 1
    FROM permission_catalog_seed seed
    WHERE seed.code = permissions.code
);

INSERT INTO permissions (id, code, name, description, created_at, assignable, catalog_version)
SELECT seed.id, seed.code, seed.name, seed.description, seed.created_at, TRUE, 1
FROM permission_catalog_seed seed
WHERE NOT EXISTS (
    SELECT 1
    FROM permissions existing
    WHERE existing.code = seed.code
);

DROP TABLE permission_catalog_seed;

-- SYSTEM_ADMIN owns every canonical capability.
INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
CROSS JOIN permissions permission
WHERE role.code = 'SYSTEM_ADMIN'
  AND permission.assignable = TRUE
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      WHERE existing.role_id = role.id AND existing.permission_id = permission.id
  );

-- AUDITOR is read-only across the canonical catalog.
INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
CROSS JOIN permissions permission
WHERE role.code = 'AUDITOR'
  AND permission.assignable = TRUE
  AND permission.code LIKE '%_READ'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      WHERE existing.role_id = role.id AND existing.permission_id = permission.id
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
CROSS JOIN permissions permission
WHERE role.code = 'FARM_MANAGER'
  AND permission.code IN (
      'FARM_READ', 'FARM_WRITE', 'FARM_ADMIN',
      'CROP_CATALOG_READ',
      'CROP_CYCLE_READ', 'CROP_CYCLE_WRITE', 'CROP_CYCLE_USE',
      'WORK_READ', 'WORK_WRITE', 'WORK_USE',
      'HARVEST_READ', 'HARVEST_WRITE',
      'INVENTORY_READ', 'SALES_READ',
      'IOT_READ', 'IOT_WRITE',
      'TRACEABILITY_READ', 'TRACEABILITY_USE',
      'ASSISTANT_USE'
  )
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      WHERE existing.role_id = role.id AND existing.permission_id = permission.id
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
CROSS JOIN permissions permission
WHERE role.code = 'AGRONOMIST'
  AND permission.code IN (
      'FARM_READ', 'FARM_WRITE',
      'CROP_CATALOG_READ', 'CROP_CATALOG_WRITE',
      'CROP_CYCLE_READ', 'CROP_CYCLE_WRITE', 'CROP_CYCLE_USE',
      'WORK_READ', 'WORK_WRITE', 'WORK_USE',
      'HARVEST_READ', 'HARVEST_WRITE',
      'INVENTORY_READ', 'SALES_READ',
      'IOT_READ', 'IOT_WRITE',
      'TRACEABILITY_READ', 'TRACEABILITY_USE',
      'ASSISTANT_USE'
  )
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      WHERE existing.role_id = role.id AND existing.permission_id = permission.id
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
CROSS JOIN permissions permission
WHERE role.code = 'FIELD_WORKER'
  AND permission.code IN (
      'FARM_READ', 'CROP_CATALOG_READ', 'CROP_CYCLE_READ', 'CROP_CYCLE_USE',
      'WORK_READ', 'WORK_USE', 'HARVEST_READ', 'INVENTORY_READ', 'SALES_READ',
      'IOT_READ', 'IOT_USE', 'TRACEABILITY_READ', 'ASSISTANT_USE'
  )
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      WHERE existing.role_id = role.id AND existing.permission_id = permission.id
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
CROSS JOIN permissions permission
WHERE role.code = 'WAREHOUSE_MANAGER'
  AND permission.code IN (
      'FARM_READ', 'CROP_CATALOG_READ', 'CROP_CYCLE_READ', 'WORK_READ',
      'HARVEST_READ', 'HARVEST_WRITE',
      'INVENTORY_READ', 'INVENTORY_WRITE', 'INVENTORY_USE',
      'SALES_READ', 'SALES_USE', 'IOT_READ',
      'TRACEABILITY_READ', 'TRACEABILITY_WRITE', 'TRACEABILITY_USE',
      'ASSISTANT_USE'
  )
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      WHERE existing.role_id = role.id AND existing.permission_id = permission.id
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
CROSS JOIN permissions permission
WHERE role.code = 'SALES_STAFF'
  AND permission.code IN (
      'FARM_READ', 'CROP_CATALOG_READ', 'CROP_CYCLE_READ', 'WORK_READ',
      'HARVEST_READ', 'INVENTORY_READ', 'INVENTORY_USE',
      'SALES_READ', 'SALES_WRITE', 'SALES_USE',
      'IOT_READ', 'TRACEABILITY_READ', 'ASSISTANT_USE'
  )
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      WHERE existing.role_id = role.id AND existing.permission_id = permission.id
  );
