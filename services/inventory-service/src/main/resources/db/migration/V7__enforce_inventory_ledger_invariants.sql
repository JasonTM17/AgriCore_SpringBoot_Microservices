ALTER TABLE inventory_items
    ADD CONSTRAINT ck_inventory_items_on_hand_nonnegative
        CHECK (on_hand_quantity >= 0);

ALTER TABLE inventory_items
    ADD CONSTRAINT ck_inventory_items_reserved_nonnegative
        CHECK (reserved_quantity >= 0);

ALTER TABLE inventory_items
    ADD CONSTRAINT ck_inventory_items_reserved_within_on_hand
        CHECK (reserved_quantity <= on_hand_quantity);

ALTER TABLE stock_movements
    ADD CONSTRAINT ck_stock_movements_quantity_positive
        CHECK (quantity > 0);

CREATE UNIQUE INDEX uk_stock_movements_item_type_reference
    ON stock_movements (inventory_item_id, movement_type, reference_type, reference_id);

CREATE INDEX idx_stock_movements_item_created_at
    ON stock_movements (inventory_item_id, created_at, id);
