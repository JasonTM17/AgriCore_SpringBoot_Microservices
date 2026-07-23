package com.agricore.farm;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class EnterpriseMigrationTest {

    @Test
    void v7AddsAuditedEnterpriseRegistryWithUniqueBusinessKeys() throws Exception {
        String url = "jdbc:h2:mem:enterprise-migration-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        migrate(url, MigrationVersion.fromVersion("6"));
        migrate(url, null);

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            insertEnterprise(connection, "ENT-A", "TAX-A", "ACTIVE");
            insertEnterprise(connection, "ENT-B", null, "ACTIVE");
            insertEnterprise(connection, "ENT-C", null, "INACTIVE");
            assertThrows(SQLException.class, () ->
                    insertEnterprise(connection, "ENT-A", "TAX-D", "ACTIVE")
            );
            assertThrows(SQLException.class, () ->
                    insertEnterprise(connection, "ENT-D", "TAX-A", "ACTIVE")
            );
            assertThrows(SQLException.class, () ->
                    insertEnterprise(connection, "ENT-E", "TAX-E", "SUSPENDED")
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

    private static void insertEnterprise(
            Connection connection,
            String code,
            String taxCode,
            String status
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO enterprises (
                    id, code, name, tax_code, status, created_at, updated_at,
                    created_by, updated_by, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'test', 'test', 0)
                """)) {
            Instant now = Instant.now();
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, code);
            statement.setString(3, code);
            statement.setString(4, taxCode);
            statement.setString(5, status);
            statement.setTimestamp(6, Timestamp.from(now));
            statement.setTimestamp(7, Timestamp.from(now));
            statement.executeUpdate();
        }
    }
}
