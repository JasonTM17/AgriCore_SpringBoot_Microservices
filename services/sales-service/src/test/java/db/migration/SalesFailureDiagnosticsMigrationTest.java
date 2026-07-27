package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalesFailureDiagnosticsMigrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void migrateRedactsHistoricalRowsAndPreservesCancellationEnvelope() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:sales-diagnostic-migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
        )) {
            createLegacyTables(connection);
            UUID eventId = insertSensitiveRows(connection);
            Context context = mock(Context.class);
            when(context.getConnection()).thenReturn(connection);

            new V7__sanitize_sales_failure_diagnostics().migrate(context);

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
            assertThat(migratedPayload).doesNotContain("super-secret", "<html>");
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
        String sensitiveReason = "<html>password=super-secret</html>" + "x".repeat(8_192);
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
