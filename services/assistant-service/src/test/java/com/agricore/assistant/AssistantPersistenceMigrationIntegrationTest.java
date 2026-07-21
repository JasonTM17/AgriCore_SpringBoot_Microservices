package com.agricore.assistant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AssistantPersistenceMigrationIntegrationTest {

    private static final String ROLE_SNAPSHOT = "[\"FARM_MANAGER\"]";

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM assistant_audit_events");
        jdbc.update("DELETE FROM conversations");
    }

    @Test
    void migrationsCreateVersionedSchema() {
        Integer successfulMigrations = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL",
                Integer.class
        );

        assertThat(successfulMigrations).isEqualTo(4);
    }

    @Test
    void conversationContextMustMatchFarmPresence() {
        assertThatThrownBy(() -> insertConversation(
                UUID.randomUUID(), UUID.randomUUID(), "ENTERPRISE", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseAllowsOnlyOneActiveGenerationPerConversation() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        insertConversation(conversationId, ownerId, "ENTERPRISE");

        insertGeneration(UUID.randomUUID(), conversationId, ownerId, "key-1", "QUEUED", conversationId);

        assertThatThrownBy(() -> insertGeneration(
                UUID.randomUUID(), conversationId, ownerId, "key-missing-slot", "QUEUED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertGeneration(
                UUID.randomUUID(), conversationId, ownerId, "key-2", "RUNNING", conversationId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertGeneration(
                UUID.randomUUID(), conversationId, ownerId, "key-terminal-slot", "COMPLETED", conversationId))
                .isInstanceOf(DataIntegrityViolationException.class);

        insertGeneration(UUID.randomUUID(), conversationId, ownerId, "key-2", "COMPLETED", null);
        assertThat(count("chat_generations")).isEqualTo(2);
    }

    @Test
    void generationOwnershipAndIdempotencyAreDatabaseEnforced() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        insertConversation(conversationId, ownerId, "ENTERPRISE");
        insertGeneration(UUID.randomUUID(), conversationId, ownerId, "same-key", "COMPLETED", null);

        assertThatThrownBy(() -> insertGeneration(
                UUID.randomUUID(), conversationId, ownerId, "same-key", "FAILED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertGeneration(
                UUID.randomUUID(), conversationId, UUID.randomUUID(), "other-key", "FAILED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sequencesAreUniqueAndAuditSurvivesConversationDeletion() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID generationId = UUID.randomUUID();
        insertConversation(conversationId, ownerId, "ENTERPRISE");
        insertGeneration(generationId, conversationId, ownerId, "key-1", "COMPLETED", null);

        insertMessage(UUID.randomUUID(), conversationId, generationId, 0, "USER");
        assertThatThrownBy(() -> insertMessage(
                UUID.randomUUID(), conversationId, generationId, 0, "ASSISTANT"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertMessage(
                UUID.randomUUID(), conversationId, generationId, 1, "USER"))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertMessage(UUID.randomUUID(), conversationId, generationId, 1, "ASSISTANT");

        insertEvent(UUID.randomUUID(), generationId, 0);
        assertThatThrownBy(() -> insertEvent(UUID.randomUUID(), generationId, 0))
                .isInstanceOf(DataIntegrityViolationException.class);

        insertAudit(ownerId, conversationId, generationId);
        jdbc.update("DELETE FROM conversations WHERE id = ?", conversationId);

        assertThat(count("conversation_messages")).isZero();
        assertThat(count("chat_generations")).isZero();
        assertThat(count("generation_events")).isZero();
        assertThat(count("assistant_audit_events")).isOne();
    }

    private void insertConversation(UUID conversationId, UUID ownerId, String contextType) {
        UUID farmId = "FARM".equals(contextType) ? UUID.randomUUID() : null;
        insertConversation(conversationId, ownerId, contextType, farmId);
    }

    private void insertConversation(UUID conversationId, UUID ownerId, String contextType, UUID farmId) {
        Instant now = Instant.now();
        jdbc.update("""
                        INSERT INTO conversations (
                            id, owner_user_id, title, farm_id, status, role_snapshot,
                            context_type, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, 'OPEN', ?, ?, ?, ?)
                        """,
                conversationId, ownerId, "Test conversation", farmId, ROLE_SNAPSHOT, contextType,
                Timestamp.from(now), Timestamp.from(now));
    }

    private void insertGeneration(
            UUID generationId,
            UUID conversationId,
            UUID ownerId,
            String idempotencyKey,
            String status,
            UUID activeConversationId
    ) {
        Instant now = Instant.now();
        jdbc.update("""
                        INSERT INTO chat_generations (
                            id, conversation_id, owner_user_id, idempotency_key, request_hash,
                            status, active_conversation_id, role_snapshot, provider,
                            created_at, updated_at, queued_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'test', ?, ?, ?)
                        """,
                generationId, conversationId, ownerId, idempotencyKey, "a".repeat(64),
                status, activeConversationId, ROLE_SNAPSHOT,
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
    }

    private void insertMessage(
            UUID messageId,
            UUID conversationId,
            UUID generationId,
            long sequence,
            String role
    ) {
        jdbc.update("""
                        INSERT INTO conversation_messages (
                            id, conversation_id, generation_id, sequence_no, role, content, created_at
                        ) VALUES (?, ?, ?, ?, ?, 'message', ?)
                        """,
                messageId, conversationId, generationId, sequence, role, Timestamp.from(Instant.now()));
    }

    private void insertEvent(UUID eventId, UUID generationId, long sequence) {
        jdbc.update("""
                        INSERT INTO generation_events (
                            id, generation_id, sequence_no, event_type, payload, created_at
                        ) VALUES (?, ?, ?, 'STATUS', '{}', ?)
                        """,
                eventId, generationId, sequence, Timestamp.from(Instant.now()));
    }

    private void insertAudit(UUID ownerId, UUID conversationId, UUID generationId) {
        Instant now = Instant.now();
        jdbc.update("""
                        INSERT INTO assistant_audit_events (
                            id, owner_user_id, actor_subject, conversation_id, generation_id,
                            action, outcome, created_at, retain_until
                        ) VALUES (?, ?, ?, ?, ?, 'GENERATION_COMPLETED', 'SUCCESS', ?, ?)
                        """,
                UUID.randomUUID(), ownerId, ownerId, conversationId, generationId,
                Timestamp.from(now), Timestamp.from(now.plusSeconds(31_536_000)));
    }

    private int count(String table) {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }
}
