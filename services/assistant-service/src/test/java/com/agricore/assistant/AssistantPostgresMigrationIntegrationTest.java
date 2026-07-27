package com.agricore.assistant;

import com.agricore.assistant.application.model.ToolSource;
import com.agricore.assistant.infrastructure.configuration.AssistantRagProperties;
import com.agricore.assistant.infrastructure.rag.JdbcKnowledgeRetriever;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

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

        AssistantRagProperties properties = new AssistantRagProperties();
        properties.setEnabled(true);
        var facts = new JdbcKnowledgeRetriever(jdbc, properties).retrieve("đất");

        assertThat(facts).isNotEmpty();
        assertThat(facts.getFirst().source()).isEqualTo(ToolSource.KNOWLEDGE);
        assertThat(facts.getFirst().fields())
                .containsEntry("title", "Quản lý nông trại và lô đất");
    }
}
