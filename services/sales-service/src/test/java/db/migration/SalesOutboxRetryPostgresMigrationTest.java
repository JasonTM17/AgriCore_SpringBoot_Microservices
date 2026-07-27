package db.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class SalesOutboxRetryPostgresMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("agricore_sales_outbox")
            .withUsername("agricore")
            .withPassword("agricore_test");

    @Test
    void migrationCreatesRetryStateAndNonBlockingIndexes() throws Exception {
        try (Connection connection = POSTGRES.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE outbox_events (
                        id UUID PRIMARY KEY,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        published_at TIMESTAMP WITH TIME ZONE
                    )
                    """);
        }

        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/postgresql-migration")
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
                .baselineVersion("8")
                .load();
        flyway.baseline();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);

        try (Connection connection = POSTGRES.createConnection("")) {
            assertThat(columnExists(connection, "next_attempt_at")).isTrue();
            assertThat(columnExists(connection, "quarantined_at")).isTrue();
            assertThat(indexDefinition(connection, "idx_sales_outbox_retry_queue"))
                    .contains("WHERE ((published_at IS NULL) AND (quarantined_at IS NULL))");
            assertThat(indexDefinition(connection, "idx_sales_outbox_quarantine"))
                    .contains("WHERE (quarantined_at IS NOT NULL)");
        }

        UUID eventId = UUID.randomUUID();
        try (Connection setup = POSTGRES.createConnection("");
             var insert = setup.prepareStatement("""
                     INSERT INTO outbox_events (id, created_at, published_at, quarantined_at)
                     VALUES (?, ?, NULL, ?)
                     """)) {
            Instant expired = Instant.now().minusSeconds(8 * 24 * 60 * 60L);
            insert.setObject(1, eventId);
            insert.setTimestamp(2, Timestamp.from(expired));
            insert.setTimestamp(3, Timestamp.from(expired));
            insert.executeUpdate();
        }

        try (Connection operator = POSTGRES.createConnection("");
             Connection cleanup = POSTGRES.createConnection("")) {
            operator.setAutoCommit(false);
            cleanup.setAutoCommit(false);
            try (var lock = operator.prepareStatement(
                    "SELECT id FROM outbox_events WHERE id = ? FOR UPDATE"
            )) {
                lock.setObject(1, eventId);
                try (ResultSet locked = lock.executeQuery()) {
                    assertThat(locked.next()).isTrue();
                }
            }

            try (var candidates = cleanup.prepareStatement("""
                    SELECT id FROM outbox_events
                    WHERE quarantined_at < ?
                    ORDER BY created_at ASC
                    LIMIT 10
                    FOR UPDATE SKIP LOCKED
                    """)) {
                candidates.setTimestamp(1, Timestamp.from(Instant.now().minusSeconds(7 * 24 * 60 * 60L)));
                try (ResultSet skipped = candidates.executeQuery()) {
                    assertThat(skipped.next()).isFalse();
                }
            } finally {
                cleanup.rollback();
                operator.rollback();
            }
        }
    }

    private static boolean columnExists(Connection connection, String columnName) throws Exception {
        try (var query = connection.prepareStatement("""
                SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'outbox_events'
                  AND column_name = ?
                """)) {
            query.setString(1, columnName);
            try (ResultSet result = query.executeQuery()) {
                return result.next();
            }
        }
    }

    private static String indexDefinition(Connection connection, String indexName) throws Exception {
        try (var query = connection.prepareStatement("""
                SELECT indexdef FROM pg_indexes
                WHERE schemaname = 'public' AND indexname = ?
                """)) {
            query.setString(1, indexName);
            try (ResultSet result = query.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }
}
