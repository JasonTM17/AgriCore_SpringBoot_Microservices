package com.agricore.farm;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FarmAreaMigrationTest {

    @Test
    void v4BackfillsFarmScopedAreasWithoutChangingLegacyAssignments() throws Exception {
        String url = "jdbc:h2:mem:farm-area-migration-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        migrate(url, MigrationVersion.fromVersion("3"));

        UUID farmA = UUID.randomUUID();
        UUID farmB = UUID.randomUUID();
        UUID sharedArea = UUID.randomUUID();
        UUID farmAOnlyArea = UUID.randomUUID();
        UUID farmAPlot = UUID.randomUUID();
        UUID farmBPlot = UUID.randomUUID();
        UUID nullAreaPlot = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            insertFarm(connection, farmA, "FARM-A");
            insertFarm(connection, farmB, "FARM-B");
            insertPlot(connection, farmAPlot, farmA, sharedArea, "A-1");
            insertPlot(connection, UUID.randomUUID(), farmA, farmAOnlyArea, "A-2");
            insertPlot(connection, farmBPlot, farmB, sharedArea, "B-1");
            insertPlot(connection, nullAreaPlot, farmA, null, "A-NULL");
        }

        migrate(url, null);

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertEquals(3, scalar(connection, "SELECT COUNT(*) FROM farm_areas"));
            assertEquals(2, scalar(
                    connection,
                    "SELECT COUNT(*) FROM farm_areas WHERE id = ?",
                    sharedArea
            ));
            assertEquals(1, scalar(
                    connection,
                    "SELECT COUNT(*) FROM plots WHERE id = ? AND area_id = ?",
                    farmAPlot,
                    sharedArea
            ));
            assertNull(value(connection, "SELECT area_id FROM plots WHERE id = ?", nullAreaPlot));
            assertNull(value(
                    connection,
                    "SELECT area_in_hectares FROM farm_areas WHERE farm_id = ? AND id = ?",
                    farmA,
                    sharedArea
            ));
            assertThrows(SQLException.class, () -> assignArea(connection, farmBPlot, farmAOnlyArea));
        }
    }

    private static void migrate(String url, MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private static void insertFarm(Connection connection, UUID farmId, String code) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO farms (id, code, name, status, created_at, updated_at, version)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?, 0)
                """)) {
            Instant now = Instant.now();
            statement.setObject(1, farmId);
            statement.setString(2, code);
            statement.setString(3, code);
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setTimestamp(5, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static void insertPlot(
            Connection connection,
            UUID plotId,
            UUID farmId,
            UUID areaId,
            String code
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO plots (
                    id, farm_id, area_id, code, name, area_in_hectares, status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, 'AVAILABLE', ?, ?, 0)
                """)) {
            Instant now = Instant.now();
            statement.setObject(1, plotId);
            statement.setObject(2, farmId);
            statement.setObject(3, areaId);
            statement.setString(4, code);
            statement.setString(5, code);
            statement.setBigDecimal(6, new BigDecimal("1.0000"));
            statement.setTimestamp(7, Timestamp.from(now));
            statement.setTimestamp(8, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static long scalar(Connection connection, String sql, Object... arguments) throws SQLException {
        Object result = value(connection, sql, arguments);
        return ((Number) result).longValue();
    }

    private static Object value(Connection connection, String sql, Object... arguments) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < arguments.length; index++) {
                statement.setObject(index + 1, arguments[index]);
            }
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getObject(1);
            }
        }
    }

    private static void assignArea(Connection connection, UUID plotId, UUID areaId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE plots SET area_id = ? WHERE id = ?"
        )) {
            statement.setObject(1, areaId);
            statement.setObject(2, plotId);
            statement.executeUpdate();
        }
    }
}
