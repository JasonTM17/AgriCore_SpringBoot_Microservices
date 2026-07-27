package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Contracts the additive farm-scope migration after an operator-controlled
 * backfill. The migration fails before changing constraints when any legacy or
 * mismatched row remains, making the release gate executable.
 */
public class V8__enforce_sales_farm_scope extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        ScopeAudit audit = audit(connection);
        if (!audit.isClean()) {
            throw new SQLException(
                    """
                    Sales farm-scope preflight failed: customersWithoutFarm=%d, \
                    ordersWithoutFarm=%d, ordersWithMismatchedCustomerFarm=%d. \
                    Backfill verified farm ownership before retrying migration V8.\
                    """.formatted(
                            audit.customersWithoutFarm(),
                            audit.ordersWithoutFarm(),
                            audit.ordersWithMismatchedCustomerFarm()
                    )
            );
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE customers ALTER COLUMN farm_id SET NOT NULL");
            statement.execute("ALTER TABLE sales_orders ALTER COLUMN farm_id SET NOT NULL");
            statement.execute("""
                    ALTER TABLE customers
                    ADD CONSTRAINT uk_customers_id_farm
                    UNIQUE (id, farm_id)
                    """);
            statement.execute("""
                    ALTER TABLE sales_orders
                    ADD CONSTRAINT fk_sales_orders_customer_farm
                    FOREIGN KEY (customer_id, farm_id)
                    REFERENCES customers (id, farm_id)
                    """);
        }
    }

    private static ScopeAudit audit(Connection connection) throws SQLException {
        return new ScopeAudit(
                count(connection, "SELECT COUNT(*) FROM customers WHERE farm_id IS NULL"),
                count(connection, "SELECT COUNT(*) FROM sales_orders WHERE farm_id IS NULL"),
                count(connection, """
                        SELECT COUNT(*)
                        FROM sales_orders sales_order
                        JOIN customers customer ON customer.id = sales_order.customer_id
                        WHERE sales_order.farm_id IS NOT NULL
                          AND customer.farm_id IS NOT NULL
                          AND sales_order.farm_id <> customer.farm_id
                        """)
        );
    }

    private static long count(Connection connection, String query) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(query)) {
            if (!result.next()) {
                throw new SQLException("Sales farm-scope preflight did not return a count");
            }
            return result.getLong(1);
        }
    }

    private record ScopeAudit(
            long customersWithoutFarm,
            long ordersWithoutFarm,
            long ordersWithMismatchedCustomerFarm
    ) {
        private boolean isClean() {
            return customersWithoutFarm == 0
                    && ordersWithoutFarm == 0
                    && ordersWithMismatchedCustomerFarm == 0;
        }
    }
}
