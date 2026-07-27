CREATE TABLE inventory_batches (
    id                  UUID PRIMARY KEY,
    inventory_item_id   UUID NOT NULL REFERENCES inventory_items(id),
    lot_code            VARCHAR(100) NOT NULL,
    received_at         TIMESTAMP NOT NULL,
    expires_at          TIMESTAMP,
    quantity            NUMERIC(18, 3) NOT NULL,
    reserved_quantity   NUMERIC(18, 3) NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_inventory_batches_item_lot UNIQUE (inventory_item_id, lot_code),
    CONSTRAINT ck_inventory_batches_quantity_nonnegative CHECK (quantity >= 0),
    CONSTRAINT ck_inventory_batches_reserved_nonnegative CHECK (reserved_quantity >= 0),
    CONSTRAINT ck_inventory_batches_reserved_within_quantity CHECK (reserved_quantity <= quantity)
);

CREATE INDEX idx_inventory_batches_allocation
    ON inventory_batches (inventory_item_id, expires_at, received_at, id);

INSERT INTO inventory_batches (
    id,
    inventory_item_id,
    lot_code,
    received_at,
    expires_at,
    quantity,
    reserved_quantity,
    created_at,
    updated_at
)
SELECT
    id,
    id,
    CONCAT('LEGACY-', CAST(id AS VARCHAR(36))),
    created_at,
    NULL,
    on_hand_quantity,
    reserved_quantity,
    created_at,
    updated_at
FROM inventory_items;

CREATE TABLE inventory_reservation_allocations (
    id                  UUID PRIMARY KEY,
    reservation_id      UUID NOT NULL REFERENCES inventory_reservations(id),
    batch_id            UUID NOT NULL REFERENCES inventory_batches(id),
    quantity            NUMERIC(18, 3) NOT NULL,
    created_at          TIMESTAMP NOT NULL,
    CONSTRAINT uk_inventory_reservation_allocations_reservation_batch
        UNIQUE (reservation_id, batch_id),
    CONSTRAINT ck_inventory_reservation_allocations_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX idx_inventory_reservation_allocations_reservation
    ON inventory_reservation_allocations (reservation_id, created_at);

INSERT INTO inventory_reservation_allocations (
    id,
    reservation_id,
    batch_id,
    quantity,
    created_at
)
SELECT
    id,
    id,
    inventory_item_id,
    quantity,
    created_at
FROM inventory_reservations;

ALTER TABLE stock_movements
    ADD COLUMN batch_id UUID REFERENCES inventory_batches(id);

CREATE INDEX idx_stock_movements_batch ON stock_movements (batch_id);
