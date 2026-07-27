package db.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class IdentityOutboxRetryPostgresMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("agricore_identity_outbox")
            .withUsername("agricore")
            .withPassword("agricore_test");

    @Test
    void migrationCreatesRetryStateAndNonBlockingIndexes() throws Exception {
        UUID legacyLeaseEventId = UUID.randomUUID();
        try (Connection connection = POSTGRES.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE outbox_events (
                        id UUID PRIMARY KEY,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        published_at TIMESTAMP WITH TIME ZONE,
                        claim_until TIMESTAMP
                    )
                    """);
            try (var insert = connection.prepareStatement("""
                    INSERT INTO outbox_events (id, created_at, claim_until)
                    VALUES (?, TIMESTAMPTZ '2026-07-27 05:00:00+00', TIMESTAMP '2026-07-27 05:00:00')
                    """)) {
                insert.setObject(1, legacyLeaseEventId);
                insert.executeUpdate();
            }
        }

        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/postgresql-migration")
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
                .baselineVersion("4")
                .load();
        flyway.baseline();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(4);

        try (Connection connection = POSTGRES.createConnection("")) {
            assertThat(columnExists(connection, "next_attempt_at")).isTrue();
            assertThat(columnExists(connection, "quarantined_at")).isTrue();
            assertThat(columnDataType(connection, "claim_until"))
                    .isEqualTo("timestamp with time zone");
            assertLegacyUtcLeaseWasPreserved(connection, legacyLeaseEventId);
            assertThat(indexDefinition(connection, "idx_identity_outbox_retry_queue"))
                    .contains("WHERE ((published_at IS NULL) AND (quarantined_at IS NULL))");
            assertThat(indexDefinition(connection, "idx_identity_outbox_quarantine"))
                    .contains("WHERE (quarantined_at IS NOT NULL)");
            assertLeaseExpiryUsesAnAbsoluteInstant(connection);
        }
    }

    private static void assertLegacyUtcLeaseWasPreserved(Connection connection, UUID eventId) throws Exception {
        try (var query = connection.prepareStatement("""
                SELECT claim_until = TIMESTAMPTZ '2026-07-27 05:00:00+00'
                FROM outbox_events
                WHERE id = ?
                """)) {
            query.setObject(1, eventId);
            try (ResultSet result = query.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBoolean(1)).isTrue();
            }
        }
    }

    private static void assertLeaseExpiryUsesAnAbsoluteInstant(Connection connection) throws Exception {
        UUID eventId = UUID.randomUUID();
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET TIME ZONE 'Asia/Bangkok'");
        }
        try (var insert = connection.prepareStatement("""
                INSERT INTO outbox_events (id, created_at, claim_until)
                VALUES (?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 hour')
                """)) {
            insert.setObject(1, eventId);
            insert.executeUpdate();
        }

        assertThat(publishableCount(connection, eventId)).isZero();

        try (var expire = connection.prepareStatement("""
                UPDATE outbox_events
                SET claim_until = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE id = ?
                """)) {
            expire.setObject(1, eventId);
            expire.executeUpdate();
        }
        assertThat(publishableCount(connection, eventId)).isEqualTo(1);
    }

    private static int publishableCount(Connection connection, UUID eventId) throws Exception {
        try (var query = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM outbox_events
                WHERE id = ?
                  AND published_at IS NULL
                  AND quarantined_at IS NULL
                  AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP)
                  AND (claim_until IS NULL OR claim_until <= CURRENT_TIMESTAMP)
                """)) {
            query.setObject(1, eventId);
            try (ResultSet result = query.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1);
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
}
