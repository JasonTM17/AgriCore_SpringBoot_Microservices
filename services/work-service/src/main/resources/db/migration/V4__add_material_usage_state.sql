CREATE TABLE material_usages (
    id                      UUID PRIMARY KEY,
    work_task_id            UUID NOT NULL REFERENCES work_tasks(id),
    inventory_item_id       UUID NOT NULL,
    quantity                NUMERIC(18, 3) NOT NULL CHECK (quantity > 0),
    unit                    VARCHAR(16),
    status                  VARCHAR(32) NOT NULL,
    inventory_reference_id  VARCHAR(100) NOT NULL,
    last_error              TEXT,
    consumed_at             TIMESTAMP,
    created_at              TIMESTAMP NOT NULL,
    updated_at              TIMESTAMP NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_material_usages_task_item
    ON material_usages (work_task_id, inventory_item_id);

CREATE UNIQUE INDEX uk_material_usages_inventory_reference
    ON material_usages (inventory_reference_id);

CREATE INDEX idx_material_usages_task_status
    ON material_usages (work_task_id, status);
