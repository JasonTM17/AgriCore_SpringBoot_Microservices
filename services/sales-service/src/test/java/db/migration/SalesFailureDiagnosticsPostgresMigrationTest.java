package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class SalesFailureDiagnosticsPostgresMigrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("agricore_sales")
            .withUsername("agricore")
            .withPassword("agricore_test");

    @Test
    void flywayRedactsLegacyDiagnosticsOnPostgres() throws Exception {
        try (Connection connection = POSTGRES.createConnection("")) {
            createLegacyTables(connection);
        }

        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineVersion("6")
                .target("7")
                .load();
        flyway.baseline();

        UUID eventId;
        try (Connection connection = POSTGRES.createConnection("")) {
            eventId = insertSensitiveRows(connection);
        }

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);

        try (Connection connection = POSTGRES.createConnection("")) {
            assertThat(singleValue(connection, "SELECT failure_reason FROM sales_orders"))
                    .isEqualTo("Inventory saga failed");
            assertThat(singleValue(connection, "SELECT last_error FROM order_sagas"))
                    .isEqualTo("Inventory saga failed");
            String migratedPayload = singleValue(
                    connection,
                    "SELECT payload FROM outbox_events WHERE id = '" + eventId + "'"
            );
            JsonNode envelope = OBJECT_MAPPER.readTree(migratedPayload);
            assertThat(envelope.path("eventId").asText()).isEqualTo(eventId.toString());
            assertThat(envelope.path("payload").path("reason").asText())
                    .isEqualTo("Inventory saga failed");
            assertThat(migratedPayload).doesNotContain("postgres-secret", "<html>");
        }
    }

    private static void createLegacyTables(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sales_orders (failure_reason TEXT)");
            statement.execute("CREATE TABLE order_sagas (last_error TEXT)");
            statement.execute("""
                    CREATE TABLE outbox_events (
                        id UUID PRIMARY KEY,
                        event_type VARCHAR(255) NOT NULL,
                        payload TEXT NOT NULL
                    )
                    """);
        }
    }

    private static UUID insertSensitiveRows(Connection connection) throws Exception {
        UUID eventId = UUID.randomUUID();
        String sensitiveReason = "<html>password=postgres-secret</html>" + "x".repeat(8_192);
        try (var order = connection.prepareStatement(
                "INSERT INTO sales_orders (failure_reason) VALUES (?)"
        );
             var saga = connection.prepareStatement(
                     "INSERT INTO order_sagas (last_error) VALUES (?)"
             );
             var event = connection.prepareStatement("""
                     INSERT INTO outbox_events (id, event_type, payload)
                     VALUES (?, 'SalesOrderCancelled.v1', ?)
                     """)) {
            order.setString(1, sensitiveReason);
            order.executeUpdate();
            saga.setString(1, sensitiveReason);
            saga.executeUpdate();
            event.setObject(1, eventId);
            event.setString(2, """
                    {"eventId":"%s","payload":{"reason":"%s","finalStatus":"CANCELLED"}}
                    """.formatted(eventId, sensitiveReason));
            event.executeUpdate();
        }
        return eventId;
    }

    private static String singleValue(Connection connection, String query) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(query)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }
}
