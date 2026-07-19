package com.agricore.harvest;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HarvestOutboxMigrationTest {

    @Test
    void v3_backfillsSuccessfulLegacyPublishAttempts() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:harvest-migration-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                ""
        );
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("2"))
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        UUID firstAttemptSuccess = UUID.randomUUID();
        UUID successAfterTwoFailures = UUID.randomUUID();
        insertPublishedEvent(jdbcTemplate, firstAttemptSuccess, 0);
        insertPublishedEvent(jdbcTemplate, successAfterTwoFailures, 2);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(publishAttempts(jdbcTemplate, firstAttemptSuccess)).isEqualTo(1);
        assertThat(publishAttempts(jdbcTemplate, successAfterTwoFailures)).isEqualTo(3);
    }

    private static void insertPublishedEvent(JdbcTemplate jdbcTemplate, UUID eventId, int attempts) {
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events
                    (id, aggregate_type, aggregate_id, event_type, topic, payload, created_at,
                     published_at, publish_attempts)
                VALUES (?, 'HarvestBatch', ?, 'HarvestCompleted.v1', 'agricore.harvest.events',
                        ?, ?, ?, ?)
                """,
                eventId,
                UUID.randomUUID().toString(),
                "{\"eventId\":\"" + eventId + "\"}",
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                attempts
        );
    }

    private static Integer publishAttempts(JdbcTemplate jdbcTemplate, UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT publish_attempts FROM outbox_events WHERE id = ?",
                Integer.class,
                eventId
        );
    }
}
