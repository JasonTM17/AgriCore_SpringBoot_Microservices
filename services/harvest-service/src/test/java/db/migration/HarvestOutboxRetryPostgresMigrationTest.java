package db.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class HarvestOutboxRetryPostgresMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("agricore_harvest_outbox_migration")
            .withUsername("agricore")
            .withPassword("agricore_test");

    @Test
    void migrationCreatesUsableRetryAndQuarantineQueues() throws Exception {
        createPreRetrySchema();

        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/postgresql-migration")
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
                .baselineVersion("6")
                .load();
        flyway.baseline();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);

        UUID ready = UUID.randomUUID();
        UUID due = UUID.randomUUID();
        try (Connection connection = POSTGRES.createConnection("")) {
            assertThat(columnType(connection, "next_attempt_at")).isEqualTo("timestamp with time zone");
            assertThat(columnType(connection, "quarantined_at")).isEqualTo("timestamp with time zone");
            assertValidIndex(connection, "idx_harvest_outbox_retry_queue",
                    "published_at IS NULL", "quarantined_at IS NULL");
            assertValidIndex(connection, "idx_harvest_outbox_quarantine",
                    "quarantined_at IS NOT NULL");
            insertQueueFixture(connection, ready, due);
            assertThat(publishableIds(connection)).containsExactly(ready, due);
            assertThat(queuePlan(connection)).contains("idx_harvest_outbox_retry_queue");
        }

        assertSkipLockedPreventsConcurrentPublication(ready);
    }

    private static void createPreRetrySchema() throws Exception {
        try (Connection connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE outbox_events (
                        id UUID PRIMARY KEY,
                        created_at TIMESTAMP NOT NULL,
                        published_at TIMESTAMP
                    )
                    """);
        }
    }

    private static void insertQueueFixture(Connection connection, UUID ready, UUID due) throws Exception {
        UUID delayed = UUID.randomUUID();
        UUID quarantined = UUID.randomUUID();
        UUID published = UUID.randomUUID();
        try (var insert = connection.prepareStatement("""
                INSERT INTO outbox_events (id, created_at, published_at, next_attempt_at, quarantined_at)
                VALUES
                    (?, CURRENT_TIMESTAMP - INTERVAL '5 minutes', NULL, NULL, NULL),
                    (?, CURRENT_TIMESTAMP - INTERVAL '4 minutes', NULL,
                        CURRENT_TIMESTAMP - INTERVAL '1 minute', NULL),
                    (?, CURRENT_TIMESTAMP - INTERVAL '3 minutes', NULL,
                        CURRENT_TIMESTAMP + INTERVAL '1 hour', NULL),
                    (?, CURRENT_TIMESTAMP - INTERVAL '2 minutes', NULL, NULL, CURRENT_TIMESTAMP),
                    (?, CURRENT_TIMESTAMP - INTERVAL '1 minute', CURRENT_TIMESTAMP, NULL, NULL)
                """)) {
            insert.setObject(1, ready);
            insert.setObject(2, due);
            insert.setObject(3, delayed);
            insert.setObject(4, quarantined);
            insert.setObject(5, published);
            insert.executeUpdate();
        }
    }

    private static List<UUID> publishableIds(Connection connection) throws Exception {
        try (var query = connection.prepareStatement("""
                SELECT id FROM outbox_events
                WHERE published_at IS NULL
                  AND quarantined_at IS NULL
                  AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP)
                ORDER BY created_at
                """);
             ResultSet result = query.executeQuery()) {
            var ids = new java.util.ArrayList<UUID>();
            while (result.next()) {
                ids.add(result.getObject(1, UUID.class));
            }
            return ids;
        }
    }

    private static void assertSkipLockedPreventsConcurrentPublication(UUID eventId) throws Exception {
        try (Connection owner = POSTGRES.createConnection("");
             Connection contender = POSTGRES.createConnection("")) {
            owner.setAutoCommit(false);
            contender.setAutoCommit(false);
            try (var lock = owner.prepareStatement("SELECT id FROM outbox_events WHERE id = ? FOR UPDATE")) {
                lock.setObject(1, eventId);
                assertThat(lock.executeQuery().next()).isTrue();
            }
            try (var claim = contender.prepareStatement("""
                    SELECT id FROM outbox_events
                    WHERE id = ? AND published_at IS NULL AND quarantined_at IS NULL
                      AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP)
                    FOR UPDATE SKIP LOCKED
                    """)) {
                claim.setObject(1, eventId);
                assertThat(claim.executeQuery().next()).isFalse();
            } finally {
                contender.rollback();
                owner.rollback();
            }
        }
    }

    private static String columnType(Connection connection, String columnName) throws Exception {
        try (var query = connection.prepareStatement("""
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'outbox_events' AND column_name = ?
                """)) {
            query.setString(1, columnName);
            try (ResultSet result = query.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private static void assertValidIndex(Connection connection, String indexName, String... fragments)
            throws Exception {
        try (var query = connection.prepareStatement("""
                SELECT pg_get_indexdef(indexrelid), indisvalid, indisready
                FROM pg_index JOIN pg_class ON pg_class.oid = indexrelid
                WHERE pg_class.relname = ?
                """)) {
            query.setString(1, indexName);
            try (ResultSet result = query.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBoolean(2)).isTrue();
                assertThat(result.getBoolean(3)).isTrue();
                assertThat(result.getString(1)).contains(fragments);
            }
        }
    }

    private static String queuePlan(Connection connection) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("SET enable_seqscan = off");
            try (ResultSet result = statement.executeQuery("""
                    EXPLAIN SELECT id FROM outbox_events
                    WHERE published_at IS NULL AND quarantined_at IS NULL
                      AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP)
                    ORDER BY created_at
                    """)) {
                var plan = new StringBuilder();
                while (result.next()) {
                    plan.append(result.getString(1)).append('\n');
                }
                return plan.toString();
            }
        }
    }
}
