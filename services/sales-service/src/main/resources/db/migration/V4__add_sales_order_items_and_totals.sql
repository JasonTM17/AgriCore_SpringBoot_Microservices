ALTER TABLE sales_orders ADD COLUMN currency_code VARCHAR(3);
ALTER TABLE sales_orders ADD COLUMN subtotal_amount NUMERIC(18, 4);
ALTER TABLE sales_orders ADD COLUMN total_amount NUMERIC(18, 4);

CREATE TABLE order_items (
    id                UUID PRIMARY KEY,
    sales_order_id    UUID NOT NULL REFERENCES sales_orders(id),
    line_number       INT NOT NULL,
    inventory_item_id UUID NOT NULL,
    quantity          NUMERIC(18, 3) NOT NULL,
    unit_price        NUMERIC(18, 4),
    line_total        NUMERIC(18, 4),
    currency_code     VARCHAR(3),
    created_at        TIMESTAMP NOT NULL,
    CONSTRAINT ck_order_items_line_number_positive CHECK (line_number > 0),
    CONSTRAINT ck_order_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_order_items_unit_price_nonnegative CHECK (unit_price IS NULL OR unit_price >= 0),
    CONSTRAINT ck_order_items_line_total_nonnegative CHECK (line_total IS NULL OR line_total >= 0)
);

CREATE UNIQUE INDEX uk_order_items_order_line
    ON order_items (sales_order_id, line_number);
CREATE INDEX idx_order_items_order
    ON order_items (sales_order_id);
