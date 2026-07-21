package com.agricore.assistant;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

final class AssistantLegacyMigrationAssertions {

    private AssistantLegacyMigrationAssertions() {
    }

    static void assertHardenedUpgrade(
            JdbcTemplate jdbc,
            AssistantLegacyMigrationTestSupport.LegacyFixture fixture
    ) {
        assertThat(messageGeneration(jdbc, fixture.userMessageId())).isEqualTo(fixture.generationId());
        assertThat(messageGeneration(jdbc, fixture.assistantMessageId())).isEqualTo(fixture.generationId());
        assertThat(messageGeneration(jdbc, fixture.queuedMessageId())).isEqualTo(fixture.queuedGenerationId());
        assertThat(messageGeneration(jdbc, fixture.runningMessageId())).isEqualTo(fixture.runningGenerationId());
        assertThat(jdbc.queryForObject(
                "SELECT role_snapshot FROM conversations WHERE id = ?", String.class, fixture.conversationId()))
                .isEqualTo("[\"FARM_MANAGER\",\"AGRONOMIST\"]");
        assertThat(jdbc.queryForObject(
                "SELECT role_snapshot FROM chat_generations WHERE id = ?", String.class, fixture.generationId()))
                .isEqualTo("[\"FARM_MANAGER\",\"AGRONOMIST\"]");
        assertThat(jdbc.queryForObject(
                "SELECT tool_evidence FROM chat_generations WHERE id = ?", String.class, fixture.generationId()))
                .isEqualTo("{\"facts\":[]}");

        assertLegacyActiveFailedClosed(jdbc, fixture.queuedGenerationId());
        assertLegacyActiveFailedClosed(jdbc, fixture.runningGenerationId());

        Integer unsafeColumns = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name IN ('chat_generations', 'assistant_audit_events')
                  AND column_name IN ('user_message_id', 'assistant_message_id', 'error_message', 'detail')
                """, Integer.class);
        assertThat(unsafeColumns).isZero();

        assertThat(jdbc.queryForObject(
                "SELECT event_type FROM generation_events WHERE generation_id = ?",
                String.class,
                fixture.generationId()
        )).isEqualTo("STATUS");
        assertThat(jdbc.queryForObject(
                "SELECT next_event_sequence FROM chat_generations WHERE id = ?",
                Long.class,
                fixture.generationId()
        )).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT owner_user_id FROM assistant_audit_events WHERE id = ?",
                UUID.class,
                fixture.auditId()
        )).isEqualTo(fixture.ownerId());
        assertThat(jdbc.queryForObject(
                "SELECT actor_subject FROM assistant_audit_events WHERE id = ?",
                UUID.class,
                fixture.auditId()
        )).isEqualTo(fixture.ownerId());

        Timestamp archivedAt = jdbc.queryForObject(
                "SELECT archived_at FROM conversations WHERE id = ?",
                Timestamp.class,
                fixture.archivedConversationId()
        );
        Timestamp purgeAfter = jdbc.queryForObject(
                "SELECT purge_after FROM conversations WHERE id = ?",
                Timestamp.class,
                fixture.archivedConversationId()
        );
        assertThat(archivedAt).isNotNull();
        assertThat(purgeAfter).isAfter(Timestamp.from(Instant.now()));
    }

    private static void assertLegacyActiveFailedClosed(JdbcTemplate jdbc, UUID generationId) {
        var row = jdbc.queryForMap("""
                SELECT status, error_code, active_conversation_id
                FROM chat_generations WHERE id = ?
                """, generationId);
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("error_code")).isEqualTo("MIGRATION_WORKER_LOST");
        assertThat(row.get("active_conversation_id")).isNull();
    }

    private static UUID messageGeneration(JdbcTemplate jdbc, UUID messageId) {
        return jdbc.queryForObject(
                "SELECT generation_id FROM conversation_messages WHERE id = ?",
                UUID.class,
                messageId
        );
    }
}
