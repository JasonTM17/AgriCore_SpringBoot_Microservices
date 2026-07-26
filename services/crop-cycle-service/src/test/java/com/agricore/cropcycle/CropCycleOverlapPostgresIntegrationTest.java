package com.agricore.cropcycle;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class CropCycleOverlapPostgresIntegrationTest {

    private static final String OVERLAP_CONSTRAINT = "crop_cycles_no_active_date_overlap";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("agricore_crop_cycle")
            .withUsername("agricore")
            .withPassword("agricore_test");

    @Test
    void concurrentOverlappingInsertsAreSerializedByPostgresAndTerminalPlotCanBeReused() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/postgresql-migration")
                .load()
                .migrate();

        UUID farmId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        UUID cropId = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var first = executor.submit(() -> insertAfterGate(
                    UUID.randomUUID(),
                    "CC-PG-A-" + System.nanoTime(),
                    farmId,
                    plotId,
                    cropId,
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 6, 30),
                    ready,
                    start
            ));
            var second = executor.submit(() -> insertAfterGate(
                    UUID.randomUUID(),
                    "CC-PG-B-" + System.nanoTime(),
                    farmId,
                    plotId,
                    cropId,
                    LocalDate.of(2026, 3, 1),
                    LocalDate.of(2026, 9, 30),
                    ready,
                    start
            ));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<InsertAttempt> attempts = List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)
            );

            assertThat(attempts).filteredOn(InsertAttempt::succeeded).hasSize(1);
            InsertAttempt rejected = attempts.stream()
                    .filter(attempt -> !attempt.succeeded())
                    .findFirst()
                    .orElseThrow();
            assertThat(rejected.sqlState()).isEqualTo("23P01");
            assertThat(rejected.constraintName()).isEqualTo(OVERLAP_CONSTRAINT);
            assertThat(countForPlot(plotId)).isEqualTo(1);

            completeCyclesForPlot(plotId);
            insert(
                    UUID.randomUUID(),
                    "CC-PG-REUSE-" + System.nanoTime(),
                    farmId,
                    plotId,
                    cropId,
                    LocalDate.of(2026, 4, 1),
                    LocalDate.of(2026, 5, 1)
            );
            assertThat(countForPlot(plotId)).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    private static InsertAttempt insertAfterGate(
            UUID id,
            String code,
            UUID farmId,
            UUID plotId,
            UUID cropId,
            LocalDate plannedStart,
            LocalDate plannedEnd,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent PostgreSQL inserts did not start");
        }
        try {
            insert(id, code, farmId, plotId, cropId, plannedStart, plannedEnd);
            return InsertAttempt.success();
        } catch (SQLException exception) {
            return InsertAttempt.failure(
                    exception.getSQLState(),
                    postgresConstraintName(exception)
            );
        }
    }

    private static String postgresConstraintName(SQLException exception) {
        if (exception instanceof PSQLException postgresException
                && postgresException.getServerErrorMessage() != null) {
            return postgresException.getServerErrorMessage().getConstraint();
        }
        return null;
    }

    private static void insert(
            UUID id,
            String code,
            UUID farmId,
            UUID plotId,
            UUID cropId,
            LocalDate plannedStart,
            LocalDate plannedEnd
    ) throws SQLException {
        try (Connection connection = POSTGRES.createConnection("");
             var statement = connection.prepareStatement("""
                     INSERT INTO crop_cycles (
                         id, code, farm_id, plot_id, crop_id, planned_start_date, planned_end_date,
                         stage, status, created_at, updated_at, version
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, 'PLANNED', 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                     """)) {
            statement.setObject(1, id);
            statement.setString(2, code);
            statement.setObject(3, farmId);
            statement.setObject(4, plotId);
            statement.setObject(5, cropId);
            statement.setDate(6, Date.valueOf(plannedStart));
            if (plannedEnd == null) {
                statement.setNull(7, Types.DATE);
            } else {
                statement.setDate(7, Date.valueOf(plannedEnd));
            }
            statement.executeUpdate();
        }
    }

    private static long countForPlot(UUID plotId) throws SQLException {
        try (Connection connection = POSTGRES.createConnection("");
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM crop_cycles WHERE plot_id = ?"
             )) {
            statement.setObject(1, plotId);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static void completeCyclesForPlot(UUID plotId) throws SQLException {
        try (Connection connection = POSTGRES.createConnection("");
             var statement = connection.prepareStatement(
                     "UPDATE crop_cycles SET status = 'COMPLETED' WHERE plot_id = ?"
             )) {
            statement.setObject(1, plotId);
            statement.executeUpdate();
        }
    }

    private record InsertAttempt(boolean succeeded, String sqlState, String constraintName) {

        private static InsertAttempt success() {
            return new InsertAttempt(true, null, null);
        }

        private static InsertAttempt failure(String sqlState, String message) {
            return new InsertAttempt(false, sqlState, message);
        }
    }
}
