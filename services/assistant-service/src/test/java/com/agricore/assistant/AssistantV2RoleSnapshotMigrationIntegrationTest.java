package com.agricore.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantV2RoleSnapshotMigrationIntegrationTest {

    @Test
    void canonicalizesSnapshotsAfterV2WasAlreadyRecorded() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:assistant-v2-upgrade-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                ""
        );
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        AssistantLegacyMigrationTestSupport.migrateToVersion(dataSource, "2");

        UUID ownerId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID generationId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                        INSERT INTO conversations (
                            id, owner_user_id, title, status, role_snapshot, context_type,
                            next_message_sequence, version, created_at, updated_at
                        ) VALUES (?, ?, 'V2 legacy', 'OPEN', 'FARM_MANAGER,AGRONOMIST',
                                  'ENTERPRISE', 0, 0, ?, ?)
                        """,
                conversationId, ownerId, Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
                        INSERT INTO chat_generations (
                            id, conversation_id, owner_user_id, idempotency_key, request_hash,
                            status, role_snapshot, provider, queued_at, created_at, updated_at
                        ) VALUES (?, ?, ?, 'legacy-v2', ?, 'COMPLETED',
                                  'FARM_MANAGER,AGRONOMIST', 'none', ?, ?, ?)
                        """,
                generationId, conversationId, ownerId, "b".repeat(64),
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));

        AssistantLegacyMigrationTestSupport.migrateToLatest(dataSource);

        assertThat(jdbc.queryForObject(
                "SELECT role_snapshot FROM conversations WHERE id = ?", String.class, conversationId))
                .isEqualTo("[\"FARM_MANAGER\",\"AGRONOMIST\"]");
        assertThat(jdbc.queryForObject(
                "SELECT role_snapshot FROM chat_generations WHERE id = ?", String.class, generationId))
                .isEqualTo("[\"FARM_MANAGER\",\"AGRONOMIST\"]");
    }
}
