package com.agricore.identity;

import com.agricore.identity.api.response.RolePermissionsResponse;
import com.agricore.identity.application.service.AdminPermissionService;
import com.agricore.identity.domain.exception.IdentityException;
import com.agricore.identity.domain.model.RoleCode;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class PermissionPolicyPostgresIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("agricore_identity")
            .withUsername("agricore")
            .withPassword("agricore_test");

    @Autowired
    private AdminPermissionService permissionService;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add(
                "spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect"
        );
    }

    @Test
    void v3UpgradeRunsOnPostgresAndPreservesLegacyGrant() {
        String schema = "policy_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );

        migrate(dataSource, schema, MigrationVersion.fromVersion("2"));
        JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource);
        upgradeJdbc.update("""
                INSERT INTO %s.permissions (id, code, name, description, created_at)
                VALUES (?::uuid, 'LEGACY_POSTGRES_GRANT', 'Legacy grant', 'Upgrade fixture', NOW())
                """.formatted(schema), UUID.randomUUID());
        upgradeJdbc.update("""
                INSERT INTO %s.role_permissions (role_id, permission_id)
                SELECT role.id, permission.id
                FROM %s.roles role
                CROSS JOIN %s.permissions permission
                WHERE role.code = 'FIELD_WORKER'
                  AND permission.code = 'LEGACY_POSTGRES_GRANT'
                """.formatted(schema, schema, schema));

        migrate(dataSource, schema, null);

        assertThat(upgradeJdbc.queryForObject("""
                SELECT COUNT(*) FROM %s.flyway_schema_history
                WHERE version = '3' AND success = TRUE
                """.formatted(schema), Long.class)).isEqualTo(1L);
        assertThat(upgradeJdbc.queryForObject("""
                SELECT COUNT(*) FROM %s.permissions
                WHERE assignable = TRUE AND catalog_version = 1
                """.formatted(schema), Long.class)).isEqualTo(32L);
        assertThat(upgradeJdbc.queryForObject("""
                SELECT COUNT(*)
                FROM %s.role_permissions grant_row
                JOIN %s.roles role ON role.id = grant_row.role_id
                JOIN %s.permissions permission ON permission.id = grant_row.permission_id
                WHERE role.code = 'FIELD_WORKER'
                  AND permission.code = 'LEGACY_POSTGRES_GRANT'
                  AND permission.assignable = FALSE
                """.formatted(schema, schema, schema), Long.class)).isEqualTo(1L);
    }

    @Test
    void concurrentSameVersionUpdatesSerializeAndOnlyOneCommits() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> replaceAfterGate(
                    Set.of("FARM_READ"), "postgres-admin-1", ready, start
            ));
            var second = executor.submit(() -> replaceAfterGate(
                    Set.of("WORK_READ"), "postgres-admin-2", ready, start
            ));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<AttemptResult> results = List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)
            );

            assertThat(results).filteredOn(AttemptResult::succeeded).hasSize(1);
            AttemptResult winner = results.stream().filter(AttemptResult::succeeded).findFirst().orElseThrow();
            IdentityException conflict = (IdentityException) results.stream()
                    .filter(result -> !result.succeeded())
                    .findFirst()
                    .orElseThrow()
                    .failure();
            assertThat(conflict.getCode()).isEqualTo("POLICY_VERSION_CONFLICT");
            assertThat(conflict.getHttpStatus()).isEqualTo(409);

            assertThat(roleVersion(RoleCode.AGRONOMIST)).isEqualTo(2L);
            assertThat(auditCount(RoleCode.AGRONOMIST)).isEqualTo(1L);
            assertThat(grantedCodes(RoleCode.AGRONOMIST))
                    .containsExactly(winner.response().permissions().getFirst().code());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void auditInsertFailureRollsBackRoleVersionAndGrants() {
        List<String> before = grantedCodes(RoleCode.WAREHOUSE_MANAGER);

        assertThatThrownBy(() -> permissionService.replaceRolePermissions(
                RoleCode.WAREHOUSE_MANAGER,
                Set.of("WORK_READ"),
                1,
                "x".repeat(501),
                "postgres-admin"
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(roleVersion(RoleCode.WAREHOUSE_MANAGER)).isEqualTo(1L);
        assertThat(grantedCodes(RoleCode.WAREHOUSE_MANAGER)).containsExactlyElementsOf(before);
        assertThat(auditCount(RoleCode.WAREHOUSE_MANAGER)).isZero();
    }

    private AttemptResult replaceAfterGate(
            Set<String> codes,
            String actor,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent policy test did not start");
        }
        try {
            return new AttemptResult(
                    permissionService.replaceRolePermissions(
                            RoleCode.AGRONOMIST, codes, 1, "Concurrent PostgreSQL update", actor
                    ),
                    null
            );
        } catch (RuntimeException exception) {
            return new AttemptResult(null, exception);
        }
    }

    private long roleVersion(RoleCode roleCode) {
        return jdbc.queryForObject(
                "SELECT permission_policy_version FROM roles WHERE code = ?",
                Long.class,
                roleCode.name()
        );
    }

    private long auditCount(RoleCode roleCode) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM role_permission_policy_audits audit
                JOIN roles role ON role.id = audit.role_id
                WHERE role.code = ?
                """, Long.class, roleCode.name());
    }

    private List<String> grantedCodes(RoleCode roleCode) {
        return jdbc.queryForList("""
                SELECT permission.code
                FROM role_permissions grant_row
                JOIN roles role ON role.id = grant_row.role_id
                JOIN permissions permission ON permission.id = grant_row.permission_id
                WHERE role.code = ? AND permission.assignable = TRUE
                ORDER BY permission.code
                """, String.class, roleCode.name());
    }

    private static void migrate(DataSource dataSource, String schema, MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .defaultSchema(schema)
                .schemas(schema)
                .createSchemas(true);
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private record AttemptResult(RolePermissionsResponse response, RuntimeException failure) {
        private boolean succeeded() {
            return response != null;
        }
    }
}
