package com.agricore.assistant;

import com.agricore.assistant.infrastructure.retention.AssistantRetentionCleanupStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class AssistantRetentionCleanupPostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-26T03:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("agricore_assistant")
            .withUsername("agricore")
            .withPassword("agricore_test");

    @Autowired
    private AssistantRetentionCleanupStore cleanupStore;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private Clock clock;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM assistant_audit_events");
        jdbc.update("DELETE FROM conversations");
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void physicallyPurgesExpiredDataInBoundedBatches() {
        UUID liveConversation = insertConversation("OPEN", null);
        UUID liveGeneration = insertGeneration(liveConversation);
        UUID expiredEvent = insertEvent(liveGeneration, NOW.minus(1, ChronoUnit.MINUTES));

        UUID expiredConversation = insertConversation(
                "ARCHIVED", NOW.minus(1, ChronoUnit.MINUTES));
        UUID expiredGeneration = insertGeneration(expiredConversation);
        UUID cascadedMessage = insertMessage(expiredConversation, expiredGeneration);
        UUID cascadedEvent = insertEvent(expiredGeneration, NOW.plus(1, ChronoUnit.DAYS));

        UUID retainedConversation = insertConversation(
                "ARCHIVED", NOW.plus(1, ChronoUnit.DAYS));
        UUID retainedGeneration = insertGeneration(retainedConversation);
        UUID retainedEvent = insertEvent(retainedGeneration, NOW.plus(1, ChronoUnit.DAYS));

        UUID expiredAudit = insertAudit(NOW.minus(1, ChronoUnit.MINUTES));
        UUID retainedAudit = insertAudit(NOW.plus(1, ChronoUnit.DAYS));

        AssistantRetentionCleanupStore.CleanupResult result =
                cleanupStore.purgeExpired(NOW, 1);

        assertThat(result.generationEvents()).isOne();
        assertThat(result.conversations()).isOne();
        assertThat(result.auditEvents()).isOne();
        assertMissing("generation_events", expiredEvent);
        assertMissing("conversations", expiredConversation);
        assertMissing("chat_generations", expiredGeneration);
        assertMissing("conversation_messages", cascadedMessage);
        assertMissing("generation_events", cascadedEvent);
        assertMissing("assistant_audit_events", expiredAudit);

        assertPresent("conversations", liveConversation);
        assertPresent("conversations", retainedConversation);
        assertPresent("generation_events", retainedEvent);
        assertPresent("assistant_audit_events", retainedAudit);
    }

    private UUID insertConversation(String status, Instant purgeAfter) {
        UUID id = UUID.randomUUID();
        Instant archivedAt = "ARCHIVED".equals(status) ? NOW.minus(1, ChronoUnit.DAYS) : null;
        jdbc.update("""
                INSERT INTO conversations (
                    id, owner_user_id, title, status, role_snapshot, created_at, updated_at,
                    archived_at, context_type, next_message_sequence, version, purge_after
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ENTERPRISE', 0, 0, ?)
                """, id, UUID.randomUUID(), "Retention test", status, "FARM_MANAGER",
                timestamp(NOW.minus(2, ChronoUnit.DAYS)), timestamp(NOW),
                timestamp(archivedAt), timestamp(purgeAfter));
        return id;
    }

    private UUID insertGeneration(UUID conversationId) {
        UUID id = UUID.randomUUID();
        UUID ownerId = jdbc.queryForObject(
                "SELECT owner_user_id FROM conversations WHERE id = ?",
                UUID.class,
                conversationId
        );
        jdbc.update("""
                INSERT INTO chat_generations (
                    id, conversation_id, owner_user_id, idempotency_key, status, request_hash,
                    role_snapshot, next_event_sequence, provider, queued_at, created_at, updated_at,
                    completed_at, attempt_count, version, tool_evidence
                ) VALUES (?, ?, ?, ?, 'COMPLETED', ?, 'FARM_MANAGER', 1, 'none', ?, ?, ?, ?, 1, 0, ?)
                """, id, conversationId, ownerId, id.toString(), "a".repeat(64),
                timestamp(NOW), timestamp(NOW), timestamp(NOW), timestamp(NOW), "{\"facts\":[]}");
        return id;
    }

    private UUID insertMessage(UUID conversationId, UUID generationId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO conversation_messages (
                    id, conversation_id, role, content, generation_id, created_at, sequence_no
                ) VALUES (?, ?, 'USER', 'expired prompt', ?, ?, 0)
                """, id, conversationId, generationId, timestamp(NOW));
        return id;
    }

    private UUID insertEvent(UUID generationId, Instant expiresAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO generation_events (
                    id, generation_id, sequence_no, event_type, payload, created_at, expires_at
                ) VALUES (?, ?, 0, 'COMPLETED', '{}', ?, ?)
                """, id, generationId, timestamp(NOW), timestamp(expiresAt));
        return id;
    }

    private UUID insertAudit(Instant retainUntil) {
        UUID id = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO assistant_audit_events (
                    id, owner_user_id, action, created_at, actor_subject, outcome, retain_until
                ) VALUES (?, ?, 'RETENTION_TEST', ?, ?, 'SUCCESS', ?)
                """, id, ownerId, timestamp(NOW), ownerId, timestamp(retainUntil));
        return id;
    }

    private void assertMissing(String table, UUID id) {
        assertThat(rowCount(table, id)).isZero();
    }

    private void assertPresent(String table, UUID id) {
        assertThat(rowCount(table, id)).isOne();
    }

    private long rowCount(String table, UUID id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE id = ?",
                Long.class,
                id
        );
    }

    private static LocalDateTime timestamp(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
