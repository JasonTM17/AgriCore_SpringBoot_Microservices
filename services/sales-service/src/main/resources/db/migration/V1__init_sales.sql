CREATE TABLE customers (
    id          UUID PRIMARY KEY,
    code        VARCHAR(64) NOT NULL,
    name        VARCHAR(200) NOT NULL,
    email       VARCHAR(320),
    created_at  TIMESTAMP NOT NULL
);
CREATE UNIQUE INDEX uk_customers_code ON customers (code);

CREATE TABLE sales_orders (
    id                  UUID PRIMARY KEY,
    order_number        VARCHAR(64) NOT NULL,
    customer_id         UUID NOT NULL REFERENCES customers(id),
    status              VARCHAR(40) NOT NULL,
    inventory_item_id   UUID NOT NULL,
    quantity            NUMERIC(18, 3) NOT NULL,
    reservation_id      UUID,
    correlation_id      UUID NOT NULL,
    failure_reason      TEXT,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_sales_orders_number ON sales_orders (order_number);
CREATE INDEX idx_sales_orders_status ON sales_orders (status);

CREATE TABLE order_sagas (
    id                  UUID PRIMARY KEY,
    sales_order_id      UUID NOT NULL REFERENCES sales_orders(id),
    correlation_id      UUID NOT NULL,
    current_step        VARCHAR(64) NOT NULL,
    status              VARCHAR(32) NOT NULL,
    retry_count         INT NOT NULL DEFAULT 0,
    last_error          TEXT,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL
);
CREATE UNIQUE INDEX uk_order_sagas_order ON order_sagas (sales_order_id);
CREATE INDEX idx_order_sagas_correlation ON order_sagas (correlation_id);
