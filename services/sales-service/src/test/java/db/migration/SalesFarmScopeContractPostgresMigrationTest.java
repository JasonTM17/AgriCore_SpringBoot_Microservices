package db.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class SalesFarmScopeContractPostgresMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("agricore_sales_scope")
            .withUsername("agricore")
            .withPassword("agricore_test");

    @Test
    void migrationBlocksLegacyRowsThenEnforcesFarmConsistency() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        try (Connection connection = POSTGRES.createConnection("")) {
            createExpandedSchema(connection);
            insertLegacyRows(connection, customerId, orderId);
        }

        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineVersion("7")
                .load();
        flyway.baseline();

        assertThatThrownBy(flyway::migrate)
                .hasRootCauseMessage(
                        "Sales farm-scope preflight failed: customersWithoutFarm=1, "
                                + "ordersWithoutFarm=1, ordersWithMismatchedCustomerFarm=0. "
                                + "Backfill verified farm ownership before retrying migration V8."
                );

        UUID farmId = UUID.randomUUID();
        try (Connection connection = POSTGRES.createConnection("");
             var customer = connection.prepareStatement(
                     "UPDATE customers SET farm_id = ? WHERE id = ?"
             );
             var order = connection.prepareStatement(
                     "UPDATE sales_orders SET farm_id = ? WHERE id = ?"
             )) {
            customer.setObject(1, farmId);
            customer.setObject(2, customerId);
            assertThat(customer.executeUpdate()).isEqualTo(1);
            order.setObject(1, farmId);
            order.setObject(2, orderId);
            assertThat(order.executeUpdate()).isEqualTo(1);
        }

        flyway.repair();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);

        try (Connection connection = POSTGRES.createConnection("")) {
            assertThatThrownBy(() -> insertCustomerWithoutFarm(connection))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertOrderForDifferentFarm(connection, customerId))
                    .isInstanceOf(SQLException.class);
        }
    }

    private static void createExpandedSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE customers (
                        id UUID PRIMARY KEY,
                        farm_id UUID
                    )
                    """);
            statement.execute("""
                    CREATE TABLE sales_orders (
                        id UUID PRIMARY KEY,
                        customer_id UUID NOT NULL REFERENCES customers(id),
                        farm_id UUID
                    )
                    """);
        }
    }

    private static void insertLegacyRows(Connection connection, UUID customerId, UUID orderId)
            throws SQLException {
        try (var customer = connection.prepareStatement(
                "INSERT INTO customers (id, farm_id) VALUES (?, NULL)"
        );
             var order = connection.prepareStatement(
                     "INSERT INTO sales_orders (id, customer_id, farm_id) VALUES (?, ?, NULL)"
             )) {
            customer.setObject(1, customerId);
            customer.executeUpdate();
            order.setObject(1, orderId);
            order.setObject(2, customerId);
            order.executeUpdate();
        }
    }

    private static void insertCustomerWithoutFarm(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO customers (id, farm_id) VALUES (?, NULL)"
        )) {
            statement.setObject(1, UUID.randomUUID());
            statement.executeUpdate();
        }
    }

    private static void insertOrderForDifferentFarm(Connection connection, UUID customerId)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO sales_orders (id, customer_id, farm_id)
                VALUES (?, ?, ?)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, customerId);
            statement.setObject(3, UUID.randomUUID());
            statement.executeUpdate();
        }
    }
}
