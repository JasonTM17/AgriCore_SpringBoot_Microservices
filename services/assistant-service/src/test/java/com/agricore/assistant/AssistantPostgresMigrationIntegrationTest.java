package com.agricore.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

@Testcontainers
class AssistantPostgresMigrationIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("agricore_assistant")
            .withUsername("agricore")
            .withPassword("agricore_test");

    @Test
    void v1UpgradeAndHardenedConstraintsRunOnPostgres() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        AssistantLegacyMigrationTestSupport.migrateToV1(dataSource);
        var fixture = AssistantLegacyMigrationTestSupport.seedLegalV1Data(jdbc);
        AssistantLegacyMigrationTestSupport.migrateToLatest(dataSource);

        AssistantLegacyMigrationAssertions.assertHardenedUpgrade(jdbc, fixture);
    }
}
