package db.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class WorkOutboxRetryPostgresMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("agricore_work_outbox")
            .withUsername("agricore")
            .withPassword("agricore_test");

    @Test
    void migrationCreatesRetryStateAndConcurrentQueueIndexes() throws Exception {
        UUID legacyEventId = UUID.randomUUID();
        createVersionEightOutboxTable(legacyEventId);

        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/postgresql-migration")
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
                .baselineVersion("8")
                .load();
        flyway.baseline();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("11");

        try (Connection connection = POSTGRES.createConnection("")) {
            assertThat(columnDataType(connection, "next_attempt_at"))
                    .isEqualTo("timestamp with time zone");
            assertThat(columnDataType(connection, "quarantined_at"))
                    .isEqualTo("timestamp with time zone");
            assertThat(retryStateIsNull(connection, legacyEventId)).isTrue();
            assertRetryQueueIndex(connection);
            assertQuarantineIndex(connection);
        }

        assertQueueFilteringAndNonBlockingClaims(legacyEventId);
    }

    private static void createVersionEightOutboxTable(UUID legacyEventId) throws Exception {
        try (Connection connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE outbox_events (
                        id UUID PRIMARY KEY,
                        aggregate_type VARCHAR(100) NOT NULL,
                        aggregate_id VARCHAR(100) NOT NULL,
                        event_type VARCHAR(150) NOT NULL,
                        topic VARCHAR(200) NOT NULL,
                        payload TEXT NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        published_at TIMESTAMP,
                        publish_attempts INT NOT NULL DEFAULT 0,
                        last_error TEXT
                    )
                    """);
        }
        try (Connection connection = POSTGRES.createConnection("");
             var insert = connection.prepareStatement("""
                     INSERT INTO outbox_events (
                         id, aggregate_type, aggregate_id, event_type, topic, payload, created_at
                     ) VALUES (?, 'WorkTask', 'legacy-task', 'WorkTaskCreated.v1', 'work.events', '{}', ?)
                     """)) {
            insert.setObject(1, legacyEventId);
            insert.setTimestamp(2, Timestamp.from(Instant.now().minusSeconds(600)));
            insert.executeUpdate();
        }
    }

    private static void assertQueueFilteringAndNonBlockingClaims(UUID legacyEventId) throws Exception {
        Instant queryTime = Instant.now();
        UUID dueEventId = UUID.randomUUID();
        UUID deferredEventId = UUID.randomUUID();
        UUID quarantinedEventId = UUID.randomUUID();
        UUID publishedEventId = UUID.randomUUID();

        try (Connection connection = POSTGRES.createConnection("")) {
            insertEvent(connection, dueEventId, queryTime.minusSeconds(300), null,
                    queryTime.minusSeconds(60), null);
            insertEvent(connection, deferredEventId, queryTime.minusSeconds(240), null,
                    queryTime.plusSeconds(3600), null);
            insertEvent(connection, quarantinedEventId, queryTime.minusSeconds(180), null,
                    null, queryTime.minusSeconds(30));
            insertEvent(connection, publishedEventId, queryTime.minusSeconds(120),
                    queryTime.minusSeconds(15), null, null);

            assertThat(publishableEventIds(connection, queryTime, false))
                    .containsExactly(legacyEventId, dueEventId);
        }

        try (Connection publisher = POSTGRES.createConnection("");
             Connection competingPublisher = POSTGRES.createConnection("")) {
            publisher.setAutoCommit(false);
            competingPublisher.setAutoCommit(false);
            try (var lock = publisher.prepareStatement(
                    "SELECT id FROM outbox_events WHERE id = ? FOR UPDATE"
            )) {
                lock.setObject(1, legacyEventId);
                try (ResultSet locked = lock.executeQuery()) {
                    assertThat(locked.next()).isTrue();
                }
            }

            try {
                assertThat(publishableEventIds(competingPublisher, queryTime, true))
                        .containsExactly(dueEventId);
            } finally {
                competingPublisher.rollback();
                publisher.rollback();
            }
        }
    }

    private static void insertEvent(
            Connection connection,
            UUID eventId,
            Instant createdAt,
            Instant publishedAt,
            Instant nextAttemptAt,
            Instant quarantinedAt
    ) throws Exception {
        try (var insert = connection.prepareStatement("""
                INSERT INTO outbox_events (
                    id, aggregate_type, aggregate_id, event_type, topic, payload,
                    created_at, published_at, next_attempt_at, quarantined_at
                ) VALUES (?, 'WorkTask', ?, 'WorkTaskUpdated.v1', 'work.events', '{}', ?, ?, ?, ?)
                """)) {
            insert.setObject(1, eventId);
            insert.setString(2, eventId.toString());
            insert.setTimestamp(3, Timestamp.from(createdAt));
            insert.setTimestamp(4, timestamp(publishedAt));
            insert.setObject(5, offsetDateTime(nextAttemptAt));
            insert.setObject(6, offsetDateTime(quarantinedAt));
            insert.executeUpdate();
        }
    }

    private static List<UUID> publishableEventIds(
            Connection connection,
            Instant queryTime,
            boolean skipLocked
    ) throws Exception {
        String lockingClause = skipLocked ? " FOR UPDATE SKIP LOCKED" : "";
        try (var query = connection.prepareStatement("""
                SELECT id
                FROM outbox_events
                WHERE published_at IS NULL
                  AND quarantined_at IS NULL
                  AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                ORDER BY created_at ASC
                """ + lockingClause)) {
            query.setObject(1, OffsetDateTime.ofInstant(queryTime, ZoneOffset.UTC));
            try (ResultSet result = query.executeQuery()) {
                List<UUID> ids = new ArrayList<>();
                while (result.next()) {
                    ids.add(result.getObject(1, UUID.class));
                }
                return ids;
            }
        }
    }

    private static boolean retryStateIsNull(Connection connection, UUID eventId) throws Exception {
        try (var query = connection.prepareStatement("""
                SELECT next_attempt_at IS NULL AND quarantined_at IS NULL
                FROM outbox_events
                WHERE id = ?
                """)) {
            query.setObject(1, eventId);
            try (ResultSet result = query.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBoolean(1);
            }
        }
    }

    private static String columnDataType(Connection connection, String columnName) throws Exception {
        try (var query = connection.prepareStatement("""
                SELECT data_type
                FROM information_schema.columns
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

    private static void assertRetryQueueIndex(Connection connection) throws Exception {
        assertThat(indexDefinition(connection, "idx_work_outbox_retry_queue"))
                .contains("(created_at, next_attempt_at)")
                .contains("WHERE ((published_at IS NULL) AND (quarantined_at IS NULL))");
        assertThat(indexIsValid(connection, "idx_work_outbox_retry_queue")).isTrue();
    }

    private static void assertQuarantineIndex(Connection connection) throws Exception {
        assertThat(indexDefinition(connection, "idx_work_outbox_quarantine"))
                .contains("(quarantined_at)")
                .contains("WHERE (quarantined_at IS NOT NULL)");
        assertThat(indexIsValid(connection, "idx_work_outbox_quarantine")).isTrue();
    }

    private static String indexDefinition(Connection connection, String indexName) throws Exception {
        try (var query = connection.prepareStatement("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'public' AND indexname = ?
                """)) {
            query.setString(1, indexName);
            try (ResultSet result = query.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private static boolean indexIsValid(Connection connection, String indexName) throws Exception {
        try (var query = connection.prepareStatement("""
                SELECT index_state.indisready AND index_state.indisvalid
                FROM pg_index index_state
                JOIN pg_class index_relation ON index_relation.oid = index_state.indexrelid
                JOIN pg_namespace index_namespace ON index_namespace.oid = index_relation.relnamespace
                WHERE index_namespace.nspname = 'public' AND index_relation.relname = ?
                """)) {
            query.setString(1, indexName);
            try (ResultSet result = query.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBoolean(1);
            }
        }
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static OffsetDateTime offsetDateTime(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
