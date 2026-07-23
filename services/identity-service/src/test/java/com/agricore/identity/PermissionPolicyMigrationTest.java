package com.agricore.identity;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionPolicyMigrationTest {

    @Test
    void migrationSeedsCanonicalPolicyAndPreservesLegacyRowsAndGrants() throws Exception {
        String databaseName = "permission_policy_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .target("2")
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO permissions (id, code, name, description, created_at)
                    VALUES (
                        '33333333-3333-3333-3333-333333333333',
                        'LEGACY_RUNTIME_CODE',
                        'Legacy runtime permission',
                        'Must survive the canonical policy migration',
                        TIMESTAMP '2026-07-22 00:00:00'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO role_permissions (role_id, permission_id)
                    SELECT role.id, permission.id
                    FROM roles role
                    CROSS JOIN permissions permission
                    WHERE role.code = 'FIELD_WORKER'
                      AND permission.code = 'LEGACY_RUNTIME_CODE'
                    """);
            statement.executeUpdate("""
                    INSERT INTO permissions (id, code, name, description, created_at)
                    VALUES (
                        '44444444-4444-4444-4444-444444444444',
                        'FARM_READ',
                        'Misleading runtime label',
                        'Must be replaced by canonical metadata without changing the identifier',
                        TIMESTAMP '2026-07-22 00:00:00'
                    )
                    """);
        }

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("""
                    SELECT assignable, catalog_version
                    FROM permissions
                    WHERE code = 'LEGACY_RUNTIME_CODE'
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBoolean("assignable")).isFalse();
                assertThat(result.getObject("catalog_version")).isNull();
            }

            assertThat(singleLong(statement, """
                    SELECT COUNT(*)
                    FROM role_permissions grant_row
                    JOIN roles role ON role.id = grant_row.role_id
                    JOIN permissions permission ON permission.id = grant_row.permission_id
                    WHERE role.code = 'FIELD_WORKER'
                      AND permission.code = 'LEGACY_RUNTIME_CODE'
                    """)).isEqualTo(1);
            assertThat(singleLong(statement, """
                    SELECT COUNT(*)
                    FROM permissions
                    WHERE code = 'FARM_READ'
                      AND id = '44444444-4444-4444-4444-444444444444'
                      AND name = 'Read farms'
                      AND assignable = TRUE
                      AND catalog_version = 1
                    """)).isEqualTo(1);
            assertThat(singleLong(statement, "SELECT COUNT(*) FROM permissions WHERE assignable = TRUE"))
                    .isEqualTo(32);
            assertThat(singleLong(statement, """
                    SELECT COUNT(*)
                    FROM role_permissions grant_row
                    JOIN roles role ON role.id = grant_row.role_id
                    JOIN permissions permission ON permission.id = grant_row.permission_id
                    WHERE role.code = 'SYSTEM_ADMIN'
                      AND permission.assignable = TRUE
                    """)).isEqualTo(32);
            assertThat(singleLong(statement, """
                    SELECT COUNT(*)
                    FROM role_permissions grant_row
                    JOIN roles role ON role.id = grant_row.role_id
                    JOIN permissions permission ON permission.id = grant_row.permission_id
                    WHERE role.code = 'AUDITOR'
                      AND permission.assignable = TRUE
                      AND permission.code LIKE '%_READ'
                    """)).isEqualTo(12);
            assertThat(singleLong(statement, """
                    SELECT COUNT(*)
                    FROM permissions
                    WHERE code = 'ASSISTANT_USE'
                      AND id = '22222222-2222-2222-2222-222222222032'
                      AND catalog_version = 1
                    """)).isEqualTo(1);
            assertThat(singleLong(statement, """
                    SELECT COUNT(*)
                    FROM roles
                    WHERE permission_policy_version = 1
                    """)).isEqualTo(7);
        }
    }

    private static long singleLong(java.sql.Statement statement, String sql) throws Exception {
        try (var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }
}
