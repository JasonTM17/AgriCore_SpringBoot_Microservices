package com.agricore.harvest;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HarvestOutboxMigrationTest {

    @Test
    void v4_backfillsStartedAtAndAllowsAnInProgressBatchWithoutResults() {
        DriverManagerDataSource dataSource = dataSource("harvest-lifecycle-migration-");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("3"))
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        UUID legacyId = UUID.randomUUID();
        Instant harvestedAt = Instant.parse("2026-07-20T08:30:00Z");
        insertCompletedHarvest(jdbcTemplate, legacyId, harvestedAt);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        Timestamp startedAt = jdbcTemplate.queryForObject(
                "SELECT started_at FROM harvest_batches WHERE id = ?",
                Timestamp.class,
                legacyId
        );
        assertThat(startedAt).isEqualTo(Timestamp.from(harvestedAt));

        int inserted = jdbcTemplate.update(
                """
                INSERT INTO harvest_batches
                    (id, code, crop_cycle_id, plot_id, warehouse_id, product_code, status,
                     started_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 'COFFEE', 'IN_PROGRESS', ?, ?, ?, 0)
                """,
                UUID.randomUUID(),
                "IN-PROGRESS-" + System.nanoTime(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now())
        );
        assertThat(inserted).isEqualTo(1);
    }

    @Test
    void v3_backfillsSuccessfulLegacyPublishAttempts() {
        DriverManagerDataSource dataSource = dataSource("harvest-migration-");
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

    private static DriverManagerDataSource dataSource(String namePrefix) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + namePrefix + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                ""
        );
    }

    private static void insertCompletedHarvest(JdbcTemplate jdbcTemplate, UUID id, Instant harvestedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO harvest_batches
                    (id, code, crop_cycle_id, plot_id, warehouse_id, product_code,
                     gross_weight_kg, net_weight_kg, quality_grade, status, harvested_at,
                     created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 'COFFEE', ?, ?, 'GRADE_A', 'COMPLETED', ?, ?, ?, 0)
                """,
                id,
                "LEGACY-" + System.nanoTime(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("10.000"),
                new BigDecimal("9.000"),
                Timestamp.from(harvestedAt),
                Timestamp.from(harvestedAt),
                Timestamp.from(harvestedAt)
        );
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
