package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Removes error text retained before downstream failures were converted to
 * structured, safe diagnostics.
 */
public class V7__sanitize_sales_failure_diagnostics extends BaseJavaMigration {

    private static final String SAFE_FAILURE = "Inventory saga failed";
    private static final String CANCELLATION_EVENT = "SalesOrderCancelled.v1";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        redactStoredDiagnostics(connection);
        redactCancellationEvents(connection);
    }

    private static void redactStoredDiagnostics(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE sales_orders
                    SET failure_reason = 'Inventory saga failed'
                    WHERE failure_reason IS NOT NULL
                      AND failure_reason <> 'reconciled:RELEASE'
                    """);
            statement.executeUpdate("""
                    UPDATE order_sagas
                    SET last_error = 'Inventory saga failed'
                    WHERE last_error IS NOT NULL
                    """);
        }
    }

    private void redactCancellationEvents(Connection connection) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT id, payload
                FROM outbox_events
                WHERE event_type = ?
                """);
             PreparedStatement update = connection.prepareStatement("""
                     UPDATE outbox_events
                     SET payload = ?
                     WHERE id = ?
                     """)) {
            select.setString(1, CANCELLATION_EVENT);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    UUID eventId = rows.getObject("id", UUID.class);
                    String sanitizedPayload = sanitizedPayload(rows.getString("payload"), eventId);
                    update.setString(1, sanitizedPayload);
                    update.setObject(2, eventId);
                    update.addBatch();
                }
            }
            update.executeBatch();
        }
    }

    private String sanitizedPayload(String payload, UUID eventId) throws SQLException {
        try {
            JsonNode envelope = objectMapper.readTree(payload);
            if (!(envelope instanceof ObjectNode objectEnvelope)
                    || !(objectEnvelope.path("payload") instanceof ObjectNode eventPayload)) {
                throw new SQLException("Cannot sanitize malformed sales cancellation event " + eventId);
            }
            eventPayload.put("reason", SAFE_FAILURE);
            return objectMapper.writeValueAsString(objectEnvelope);
        } catch (SQLException exception) {
            throw exception;
        } catch (Exception ignored) {
            throw new SQLException("Cannot sanitize sales cancellation event " + eventId);
        }
    }
}
