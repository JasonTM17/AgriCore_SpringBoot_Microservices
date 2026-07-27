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
class TraceabilityOutboxRetryPostgresMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("agricore_traceability_outbox")
            .withUsername("agricore")
            .withPassword("agricore_test");

    @Test
    void migrationCreatesRetryStateAndOperationalQueueIndexes() throws Exception {
        createPreRetryOutboxTable();

        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/postgresql-migration")
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
                .baselineVersion("4")
                .load();
        flyway.baseline();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);

        try (Connection connection = POSTGRES.createConnection("")) {
            assertThat(columnDataType(connection, "next_attempt_at"))
                    .isEqualTo("timestamp with time zone");
            assertThat(columnDataType(connection, "quarantined_at"))
                    .isEqualTo("timestamp with time zone");
            assertThat(indexDefinition(connection, "idx_traceability_outbox_retry_queue"))
                    .contains("(created_at, next_attempt_at)")
                    .contains("WHERE ((published_at IS NULL) AND (quarantined_at IS NULL))");
            assertThat(indexDefinition(connection, "idx_traceability_outbox_quarantine"))
                    .contains("(quarantined_at)")
                    .contains("WHERE (quarantined_at IS NOT NULL)");
            assertThat(indexIsReadyAndValid(connection, "idx_traceability_outbox_retry_queue")).isTrue();
            assertThat(indexIsReadyAndValid(connection, "idx_traceability_outbox_quarantine")).isTrue();
            assertQueueEligibility(connection);
        }

        assertLockedRowsAreSkipped();
    }

    private static void createPreRetryOutboxTable() throws Exception {
        try (Connection connection = POSTGRES.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE outbox_events (
                        id UUID PRIMARY KEY,
                        created_at TIMESTAMP NOT NULL,
                        published_at TIMESTAMP
                    )
                    """);
        }
    }

    private static void assertQueueEligibility(Connection connection) throws Exception {
        Instant now = Instant.parse("2026-07-27T05:00:00Z");
        UUID eligible = insertEvent(connection, now.minusSeconds(60), null, null, null);
        insertEvent(connection, now.minusSeconds(50), null, now.plusSeconds(60), null);
        insertEvent(connection, now.minusSeconds(40), null, null, now.minusSeconds(10));
        insertEvent(connection, now.minusSeconds(30), now.minusSeconds(20), null, null);

        try (var query = connection.prepareStatement("""
                SELECT id FROM outbox_events
                WHERE published_at IS NULL
                  AND quarantined_at IS NULL
                  AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                ORDER BY created_at ASC
                """)) {
            query.setTimestamp(1, Timestamp.from(now));
            try (ResultSet result = query.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getObject(1, UUID.class)).isEqualTo(eligible);
                assertThat(result.next()).isFalse();
            }
        }
    }

    private static void assertLockedRowsAreSkipped() throws Exception {
        UUID eventId;
        try (Connection connection = POSTGRES.createConnection("")) {
            eventId = insertEvent(connection, Instant.now(), null, null, null);
        }

        try (Connection publisher = POSTGRES.createConnection("");
             Connection competingPublisher = POSTGRES.createConnection("")) {
            publisher.setAutoCommit(false);
            competingPublisher.setAutoCommit(false);
            try (var lock = publisher.prepareStatement(
                    "SELECT id FROM outbox_events WHERE id = ? FOR UPDATE")) {
                lock.setObject(1, eventId);
                try (ResultSet locked = lock.executeQuery()) {
                    assertThat(locked.next()).isTrue();
                }
            }

            try (var candidate = competingPublisher.prepareStatement("""
                    SELECT id FROM outbox_events
                    WHERE id = ?
                      AND published_at IS NULL
                      AND quarantined_at IS NULL
                      AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP)
                    FOR UPDATE SKIP LOCKED
                    """)) {
                candidate.setObject(1, eventId);
                try (ResultSet skipped = candidate.executeQuery()) {
                    assertThat(skipped.next()).isFalse();
                }
            } finally {
                competingPublisher.rollback();
                publisher.rollback();
            }
        }
    }

    private static UUID insertEvent(
            Connection connection,
            Instant createdAt,
            Instant publishedAt,
            Instant nextAttemptAt,
            Instant quarantinedAt
    ) throws Exception {
        UUID eventId = UUID.randomUUID();
        try (var insert = connection.prepareStatement("""
                INSERT INTO outbox_events (
                    id, created_at, published_at, next_attempt_at, quarantined_at
                ) VALUES (?, ?, ?, ?, ?)
                """)) {
            insert.setObject(1, eventId);
            insert.setTimestamp(2, Timestamp.from(createdAt));
            insert.setTimestamp(3, timestamp(publishedAt));
            insert.setTimestamp(4, timestamp(nextAttemptAt));
            insert.setTimestamp(5, timestamp(quarantinedAt));
            insert.executeUpdate();
        }
        return eventId;
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static String columnDataType(Connection connection, String columnName) throws Exception {
        try (var query = connection.prepareStatement("""
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'outbox_events'
                  AND column_name = ?
                """)) {
            query.setString(1, columnName);
            try (ResultSet result = query.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
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

    private static boolean indexIsReadyAndValid(Connection connection, String indexName) throws Exception {
        try (var query = connection.prepareStatement("""
                SELECT pg_idx.indisready AND pg_idx.indisvalid
                FROM pg_index pg_idx
                JOIN pg_class relation ON relation.oid = pg_idx.indexrelid
                WHERE relation.relname = ?
                """)) {
            query.setString(1, indexName);
            try (ResultSet result = query.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBoolean(1);
            }
        }
    }
}
