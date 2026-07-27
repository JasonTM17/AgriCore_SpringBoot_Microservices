package com.agricore.farm;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class IrrigationZoneMigrationTest {

    @Test
    void v6AddsFarmScopedZonesWithOperationalConstraints() throws Exception {
        String url = "jdbc:h2:mem:irrigation-zone-migration-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        migrate(url, MigrationVersion.fromVersion("5"));
        UUID farmA = UUID.randomUUID();
        UUID farmB = UUID.randomUUID();
        UUID plotA = UUID.randomUUID();
        UUID plotB = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            insertFarm(connection, farmA, "FARM-A");
            insertFarm(connection, farmB, "FARM-B");
            insertPlot(connection, plotA, farmA, "PLOT-A");
            insertPlot(connection, plotB, farmB, "PLOT-B");
        }

        migrate(url, null);
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            insertZone(connection, farmA, plotA, "ZONE-1", "DRIP", new BigDecimal("25.00"));
            assertThrows(SQLException.class, () ->
                    insertZone(connection, farmB, plotA, "FOREIGN", "DRIP", new BigDecimal("25.00"))
            );
            assertThrows(SQLException.class, () ->
                    insertZone(connection, farmA, plotA, "ZONE-1", "DRIP", new BigDecimal("25.00"))
            );
            assertThrows(SQLException.class, () ->
                    insertZone(connection, farmB, plotB, "BAD-METHOD", "LASER", new BigDecimal("25.00"))
            );
            assertThrows(SQLException.class, () ->
                    insertZone(connection, farmB, plotB, "BAD-FLOW", "DRIP", BigDecimal.ZERO)
            );
            assertThrows(SQLException.class, () ->
                    updateStatus(connection, farmA, plotA, "BROKEN")
            );
        }
    }

    private static void migrate(String url, MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration");
        if (target != null) { configuration.target(target); }
        configuration.load().migrate();
    }

    private static void insertFarm(Connection connection, UUID id, String code)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO farms (id, code, name, status, created_at, updated_at, version)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?, 0)
                """)) {
            Instant now = Instant.now();
            statement.setObject(1, id);
            statement.setString(2, code);
            statement.setString(3, code);
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setTimestamp(5, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static void insertPlot(Connection connection, UUID id, UUID farmId, String code)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO plots (
                    id, farm_id, code, name, area_in_hectares, status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, 1.0000, 'AVAILABLE', ?, ?, 0)
                """)) {
            Instant now = Instant.now();
            statement.setObject(1, id);
            statement.setObject(2, farmId);
            statement.setString(3, code);
            statement.setString(4, code);
            statement.setTimestamp(5, Timestamp.from(now));
            statement.setTimestamp(6, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static void insertZone(
            Connection connection,
            UUID farmId,
            UUID plotId,
            String code,
            String method,
            BigDecimal flowRate
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO irrigation_zones (
                    id, farm_id, plot_id, code, name, method, flow_rate_liters_per_minute,
                    target_moisture_percent, status, created_at, updated_at, created_by,
                    updated_by, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 35.00, 'ACTIVE', ?, ?, 'test', 'test', 0)
                """)) {
            Instant now = Instant.now();
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, farmId);
            statement.setObject(3, plotId);
            statement.setString(4, code);
            statement.setString(5, code);
            statement.setString(6, method);
            statement.setBigDecimal(7, flowRate);
            statement.setTimestamp(8, Timestamp.from(now));
            statement.setTimestamp(9, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static void updateStatus(
            Connection connection,
            UUID farmId,
            UUID plotId,
            String status
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE irrigation_zones SET status = ? WHERE farm_id = ? AND plot_id = ?"
        )) {
            statement.setString(1, status);
            statement.setObject(2, farmId);
            statement.setObject(3, plotId);
            statement.executeUpdate();
        }
    }
}
