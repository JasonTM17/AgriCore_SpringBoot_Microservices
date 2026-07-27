ALTER TABLE customers ADD COLUMN farm_id UUID;
ALTER TABLE sales_orders ADD COLUMN farm_id UUID;

CREATE INDEX idx_customers_farm ON customers (farm_id);
CREATE INDEX idx_sales_orders_farm ON sales_orders (farm_id);

COMMENT ON COLUMN customers.farm_id IS
    'Authoritative farm scope; null only for records created before farm scoping was introduced.';
COMMENT ON COLUMN sales_orders.farm_id IS
    'Authoritative farm scope; null only for records created before farm scoping was introduced.';
