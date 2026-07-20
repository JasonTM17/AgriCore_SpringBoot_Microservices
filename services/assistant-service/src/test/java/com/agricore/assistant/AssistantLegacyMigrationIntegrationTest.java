package com.agricore.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.UUID;

class AssistantLegacyMigrationIntegrationTest {

    @Test
    void legalV1RowsUpgradeWithoutLosingMessageOwnershipOrAuditIdentity() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:assistant-upgrade-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                ""
        );
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        AssistantLegacyMigrationTestSupport.migrateToV1(dataSource);
        var fixture = AssistantLegacyMigrationTestSupport.seedLegalV1Data(jdbc);
        AssistantLegacyMigrationTestSupport.migrateToLatest(dataSource);

        AssistantLegacyMigrationAssertions.assertHardenedUpgrade(jdbc, fixture);
    }
}
