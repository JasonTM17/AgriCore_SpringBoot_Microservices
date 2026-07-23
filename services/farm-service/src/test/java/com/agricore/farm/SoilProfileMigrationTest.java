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
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SoilProfileMigrationTest {

    @Test
    void v5AddsFarmScopedSoilHistoryWithDatabaseConstraints() throws Exception {
        String url = "jdbc:h2:mem:soil-profile-migration-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        migrate(url, MigrationVersion.fromVersion("4"));

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
            insertProfile(connection, farmA, plotA, "SAMPLE-1", new BigDecimal("6.25"));
            assertEquals(1, countProfiles(connection, farmA, plotA));

            assertThrows(SQLException.class, () ->
                    insertProfile(connection, farmB, plotA, "FOREIGN-PLOT", new BigDecimal("6.50"))
            );
            assertThrows(SQLException.class, () ->
                    insertProfile(connection, farmA, plotA, "SAMPLE-1", new BigDecimal("6.50"))
            );
            assertThrows(SQLException.class, () ->
                    insertProfile(connection, farmB, plotB, "INVALID-PH", new BigDecimal("14.01"))
            );
            assertThrows(SQLException.class, () ->
                    updateStatus(connection, farmA, plotA, "BROKEN")
            );
            assertThrows(SQLException.class, () ->
                    updateTexture(connection, farmA, plotA, "UNKNOWN")
            );
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
            String code
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO plots (
                    id, farm_id, code, name, area_in_hectares, status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, 1.0000, 'AVAILABLE', ?, ?, 0)
                """)) {
            Instant now = Instant.now();
            statement.setObject(1, plotId);
            statement.setObject(2, farmId);
            statement.setString(3, code);
            statement.setString(4, code);
            statement.setTimestamp(5, Timestamp.from(now));
            statement.setTimestamp(6, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static void insertProfile(
            Connection connection,
            UUID farmId,
            UUID plotId,
            String sampleCode,
            BigDecimal ph
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO soil_profiles (
                    id, farm_id, plot_id, sample_code, sampled_at, sample_depth_cm, texture, ph,
                    status, created_at, updated_at, created_by, updated_by, version
                ) VALUES (?, ?, ?, ?, ?, 20.00, 'CLAY_LOAM', ?, 'ACTIVE', ?, ?, 'test', 'test', 0)
                """)) {
            Instant now = Instant.now();
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, farmId);
            statement.setObject(3, plotId);
            statement.setString(4, sampleCode);
            statement.setObject(5, LocalDate.of(2026, 1, 1));
            statement.setBigDecimal(6, ph);
            statement.setTimestamp(7, Timestamp.from(now));
            statement.setTimestamp(8, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static long countProfiles(Connection connection, UUID farmId, UUID plotId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM soil_profiles WHERE farm_id = ? AND plot_id = ?"
        )) {
            statement.setObject(1, farmId);
            statement.setObject(2, plotId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static void updateStatus(
            Connection connection,
            UUID farmId,
            UUID plotId,
            String status
    ) throws SQLException {
        updateProfileValue(
                connection,
                "UPDATE soil_profiles SET status = ? WHERE farm_id = ? AND plot_id = ?",
                status,
                farmId,
                plotId
        );
    }

    private static void updateTexture(
            Connection connection,
            UUID farmId,
            UUID plotId,
            String texture
    ) throws SQLException {
        updateProfileValue(
                connection,
                "UPDATE soil_profiles SET texture = ? WHERE farm_id = ? AND plot_id = ?",
                texture,
                farmId,
                plotId
        );
    }

    private static void updateProfileValue(
            Connection connection,
            String sql,
            String value,
            UUID farmId,
            UUID plotId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            statement.setObject(2, farmId);
            statement.setObject(3, plotId);
            statement.executeUpdate();
        }
    }
}
