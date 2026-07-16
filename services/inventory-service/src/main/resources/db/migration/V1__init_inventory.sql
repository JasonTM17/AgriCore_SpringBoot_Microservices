CREATE TABLE warehouses (
    id          UUID PRIMARY KEY,
    code        VARCHAR(64) NOT NULL,
    name        VARCHAR(200) NOT NULL,
    created_at  TIMESTAMP NOT NULL
);
CREATE UNIQUE INDEX uk_warehouses_code ON warehouses (code);

CREATE TABLE inventory_items (
    id                  UUID PRIMARY KEY,
    warehouse_id        UUID NOT NULL REFERENCES warehouses(id),
    sku                 VARCHAR(64) NOT NULL,
    name                VARCHAR(200) NOT NULL,
    item_type           VARCHAR(32) NOT NULL,
    unit                VARCHAR(16) NOT NULL,
    on_hand_quantity    NUMERIC(18, 3) NOT NULL DEFAULT 0,
    reserved_quantity   NUMERIC(18, 3) NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_inventory_items_wh_sku ON inventory_items (warehouse_id, sku);

CREATE TABLE stock_movements (
    id                  UUID PRIMARY KEY,
    inventory_item_id   UUID NOT NULL REFERENCES inventory_items(id),
    movement_type       VARCHAR(32) NOT NULL,
    quantity            NUMERIC(18, 3) NOT NULL,
    reference_type      VARCHAR(64) NOT NULL,
    reference_id        VARCHAR(100) NOT NULL,
    note                TEXT,
    created_at          TIMESTAMP NOT NULL
);
CREATE INDEX idx_stock_movements_item ON stock_movements (inventory_item_id);
CREATE INDEX idx_stock_movements_ref ON stock_movements (reference_type, reference_id);

CREATE TABLE inventory_reservations (
    id                  UUID PRIMARY KEY,
    inventory_item_id   UUID NOT NULL REFERENCES inventory_items(id),
    quantity            NUMERIC(18, 3) NOT NULL,
    status              VARCHAR(32) NOT NULL,
    reference_type      VARCHAR(64) NOT NULL,
    reference_id        VARCHAR(100) NOT NULL,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_reservations_item ON inventory_reservations (inventory_item_id);

-- Idempotent consumer ledger
CREATE TABLE processed_events (
    event_id        VARCHAR(100) NOT NULL,
    consumer_name   VARCHAR(100) NOT NULL,
    processed_at    TIMESTAMP NOT NULL,
    PRIMARY KEY (event_id, consumer_name)
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(150) NOT NULL,
    topic VARCHAR(200) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    publish_attempts INT NOT NULL DEFAULT 0,
    last_error TEXT
);
