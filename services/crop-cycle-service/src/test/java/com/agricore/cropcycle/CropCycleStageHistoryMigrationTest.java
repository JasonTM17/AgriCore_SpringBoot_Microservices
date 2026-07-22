package com.agricore.cropcycle;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CropCycleStageHistoryMigrationTest {

    @Test
    void v3_backfillsCurrentStateForExistingCycles() {
        DriverManagerDataSource dataSource = dataSource();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("2"))
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID cycleId = UUID.randomUUID();
        Instant updatedAt = Instant.parse("2026-07-20T08:30:00Z");
        jdbc.update(
                """
                INSERT INTO crop_cycles (
                    id, code, farm_id, plot_id, crop_id, planned_start_date,
                    stage, status, notes, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, 'GROWING', 'ACTIVE', ?, ?, ?, 4)
                """,
                cycleId,
                "LEGACY-" + System.nanoTime(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.parse("2026-03-01"),
                "Legacy state",
                Timestamp.from(updatedAt.minusSeconds(3600)),
                Timestamp.from(updatedAt)
        );

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        Map<String, Object> history = jdbc.queryForMap(
                """
                SELECT id, crop_cycle_id, previous_stage, stage, status, notes,
                       changed_by, changed_at, cycle_version
                FROM crop_cycle_stage_history
                WHERE crop_cycle_id = ?
                """,
                cycleId
        );
        assertThat(history.get("id")).isEqualTo(cycleId);
        assertThat(history.get("crop_cycle_id")).isEqualTo(cycleId);
        assertThat(history.get("previous_stage")).isNull();
        assertThat(history.get("stage")).isEqualTo("GROWING");
        assertThat(history.get("status")).isEqualTo("ACTIVE");
        assertThat(history.get("notes")).isEqualTo("Legacy state");
        assertThat(history.get("changed_by")).isEqualTo("migration");
        assertThat(history.get("changed_at")).isEqualTo(Timestamp.from(updatedAt));
        assertThat(history.get("cycle_version")).isEqualTo(4L);
    }

    private static DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:crop-cycle-history-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                ""
        );
    }
}
