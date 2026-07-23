package com.agricore.farm;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmEnterpriseMigrationTest {

    @Test
    void v8KeepsLegacyFarmsAndEnforcesEnterpriseReference() throws Exception {
        String url = "jdbc:h2:mem:farm-enterprise-migration-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        migrate(url, MigrationVersion.fromVersion("7"));
        UUID farmId = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            insertFarm(connection, farmId);
        }

        migrate(url, null);
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertNull(enterpriseId(connection, farmId));
            UUID enterpriseId = insertEnterprise(connection);
            updateEnterprise(connection, farmId, enterpriseId);
            assertEquals(enterpriseId, enterpriseId(connection, farmId));
            assertThrows(
                    SQLException.class,
                    () -> updateEnterprise(connection, farmId, UUID.randomUUID())
            );
            assertTrue(hasIndex(connection, "idx_farms_enterprise_id"));
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

    private static void insertFarm(Connection connection, UUID farmId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO farms (
                    id, code, name, status, created_at, updated_at, version
                ) VALUES (?, ?, 'Legacy Farm', 'ACTIVE', ?, ?, 0)
                """)) {
            Instant now = Instant.now();
            statement.setObject(1, farmId);
            statement.setString(2, "F-" + farmId);
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setTimestamp(4, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static UUID insertEnterprise(Connection connection) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO enterprises (
                    id, code, name, status, created_at, updated_at,
                    created_by, updated_by, version
                ) VALUES (?, ?, 'Enterprise', 'ACTIVE', ?, ?, 'test', 'test', 0)
                """)) {
            Instant now = Instant.now();
            statement.setObject(1, id);
            statement.setString(2, "E-" + id);
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setTimestamp(4, Timestamp.from(now));
            statement.executeUpdate();
        }
        return id;
    }

    private static void updateEnterprise(
            Connection connection,
            UUID farmId,
            UUID enterpriseId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE farms SET enterprise_id = ? WHERE id = ?"
        )) {
            statement.setObject(1, enterpriseId);
            statement.setObject(2, farmId);
            statement.executeUpdate();
        }
    }

    private static UUID enterpriseId(Connection connection, UUID farmId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT enterprise_id FROM farms WHERE id = ?"
        )) {
            statement.setObject(1, farmId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getObject(1, UUID.class);
            }
        }
    }

    private static boolean hasIndex(Connection connection, String indexName) throws SQLException {
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                null,
                null,
                "farms",
                false,
                false
        )) {
            while (indexes.next()) {
                if (indexName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
