package com.agricore.assistant;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

final class AssistantLegacyMigrationTestSupport {

    private AssistantLegacyMigrationTestSupport() {
    }

    static void migrateToV1(DataSource dataSource) {
        migrateToVersion(dataSource, "1");
    }

    static void migrateToVersion(DataSource dataSource, String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    static void migrateToLatest(DataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    static LegacyFixture seedLegalV1Data(JdbcTemplate jdbc) {
        UUID ownerId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID archivedConversationId = UUID.randomUUID();
        UUID userMessageId = UUID.randomUUID();
        UUID assistantMessageId = UUID.randomUUID();
        UUID generationId = UUID.randomUUID();
        UUID queuedGenerationId = UUID.randomUUID();
        UUID runningGenerationId = UUID.randomUUID();
        UUID queuedMessageId = UUID.randomUUID();
        UUID runningMessageId = UUID.randomUUID();
        UUID auditId = UUID.randomUUID();
        Instant now = Instant.now();

        insertConversation(jdbc, conversationId, ownerId, "OPEN", now);
        insertConversation(jdbc, archivedConversationId, ownerId, "ARCHIVED", now);
        insertMessage(jdbc, userMessageId, conversationId, "USER", now);
        insertMessage(jdbc, assistantMessageId, conversationId, "ASSISTANT", now.plusMillis(1));
        insertMessage(jdbc, queuedMessageId, conversationId, "USER", now.plusMillis(2));
        insertMessage(jdbc, runningMessageId, conversationId, "USER", now.plusMillis(3));

        jdbc.update("""
                        INSERT INTO chat_generations (
                            id, conversation_id, owner_user_id, idempotency_key, status,
                            user_message_id, assistant_message_id, error_message,
                            created_at, updated_at, completed_at
                        ) VALUES (?, ?, ?, 'legacy-key', 'COMPLETED', ?, ?, ?, ?, ?, ?)
                        """,
                generationId, conversationId, ownerId, userMessageId, assistantMessageId,
                "legacy provider detail must be removed", Timestamp.from(now), Timestamp.from(now),
                Timestamp.from(now));
        insertActiveGeneration(jdbc, queuedGenerationId, conversationId, ownerId, queuedMessageId, "QUEUED", now);
        insertActiveGeneration(jdbc, runningGenerationId, conversationId, ownerId, runningMessageId, "RUNNING", now);
        jdbc.update("""
                        INSERT INTO generation_events (
                            id, generation_id, sequence_no, event_type, payload, created_at
                        ) VALUES (?, ?, 0, 'status', '{}', ?)
                        """,
                UUID.randomUUID(), generationId, Timestamp.from(now));
        jdbc.update("""
                        INSERT INTO assistant_audit_events (
                            id, owner_user_id, conversation_id, generation_id, action, detail, created_at
                        ) VALUES (?, NULL, ?, ?, 'GENERATION_COMPLETED', ?, ?)
                        """,
                auditId, conversationId, generationId, "legacy user content must be removed", Timestamp.from(now));

        return new LegacyFixture(
                ownerId,
                conversationId,
                archivedConversationId,
                userMessageId,
                assistantMessageId,
                queuedMessageId,
                runningMessageId,
                generationId,
                queuedGenerationId,
                runningGenerationId,
                auditId
        );
    }

    private static void insertConversation(
            JdbcTemplate jdbc,
            UUID conversationId,
            UUID ownerId,
            String status,
            Instant now
    ) {
        jdbc.update("""
                        INSERT INTO conversations (
                            id, owner_user_id, title, status, role_snapshot, created_at, updated_at
                        ) VALUES (?, ?, 'Legacy conversation', ?, 'FARM_MANAGER,AGRONOMIST', ?, ?)
                        """,
                conversationId, ownerId, status, Timestamp.from(now), Timestamp.from(now));
    }

    private static void insertMessage(
            JdbcTemplate jdbc,
            UUID messageId,
            UUID conversationId,
            String role,
            Instant createdAt
    ) {
        jdbc.update("""
                        INSERT INTO conversation_messages (
                            id, conversation_id, role, content, generation_id, created_at
                        ) VALUES (?, ?, ?, 'legacy message', NULL, ?)
                        """,
                messageId, conversationId, role, Timestamp.from(createdAt));
    }

    private static void insertActiveGeneration(
            JdbcTemplate jdbc,
            UUID generationId,
            UUID conversationId,
            UUID ownerId,
            UUID userMessageId,
            String status,
            Instant now
    ) {
        jdbc.update("""
                        INSERT INTO chat_generations (
                            id, conversation_id, owner_user_id, idempotency_key, status,
                            user_message_id, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                generationId, conversationId, ownerId, "legacy-" + generationId, status,
                userMessageId, Timestamp.from(now), Timestamp.from(now));
    }

    record LegacyFixture(
            UUID ownerId,
            UUID conversationId,
            UUID archivedConversationId,
            UUID userMessageId,
            UUID assistantMessageId,
            UUID queuedMessageId,
            UUID runningMessageId,
            UUID generationId,
            UUID queuedGenerationId,
            UUID runningGenerationId,
            UUID auditId
    ) {
    }
}
