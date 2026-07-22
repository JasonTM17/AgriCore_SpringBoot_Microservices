package com.agricore.assistant;

import com.agricore.assistant.application.model.GenerationSubmissionCommand;
import com.agricore.assistant.application.model.GenerationSubmissionResult;
import com.agricore.assistant.application.model.ToolEvidenceCollection;
import com.agricore.assistant.application.model.ToolEvidenceSnapshot;
import com.agricore.assistant.application.model.ToolFact;
import com.agricore.assistant.application.model.ToolSource;
import com.agricore.assistant.application.port.GenerationRepository;
import com.agricore.assistant.domain.exception.AssistantException;
import com.agricore.assistant.domain.model.AssistantGenerationEvent;
import com.agricore.assistant.domain.model.ConversationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class GenerationPersistenceAdapterIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-20T03:00:00Z");
    private static final String HASH = "a".repeat(64);
    private static final String ROLE_SNAPSHOT = "[\"FIELD_WORKER\"]";

    @Autowired
    private GenerationRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM generation_events");
        jdbc.update("DELETE FROM conversation_messages");
        jdbc.update("DELETE FROM chat_generations");
        jdbc.update("DELETE FROM conversations");
    }

    @Test
    void submissionAtomicallyPersistsUserMessageAndQueuedEvent() {
        UUID owner = UUID.randomUUID();
        UUID conversation = insertConversation(owner, ConversationStatus.OPEN);
        ToolEvidenceSnapshot evidence = new ToolEvidenceSnapshot(List.of(
                new ToolFact("FARM-1", ToolSource.FARM, java.util.Map.of("status", "ACTIVE"))
        ));

        GenerationSubmissionResult result = repository.submit(command(
                conversation, owner, "request-1", HASH, evidence));

        assertThat(result.deduplicated()).isFalse();
        assertThat(result.generation().status()).isEqualTo(com.agricore.assistant.domain.model.GenerationStatus.QUEUED);
        assertThat(result.generation().activeConversationId()).isEqualTo(conversation);
        assertThat(result.generation().toolEvidence()).isEqualTo(evidence);
        assertThat(result.userMessage().sequenceNo()).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT next_message_sequence FROM conversations WHERE id = ?", Long.class, conversation))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conversation_messages", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM generation_events", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT event_type FROM generation_events", String.class)).isEqualTo("STATUS");
    }

    @Test
    void sameRequestIsDeduplicatedWithoutAdvancingConversationSequence() {
        UUID owner = UUID.randomUUID();
        UUID conversation = insertConversation(owner, ConversationStatus.OPEN);
        GenerationSubmissionCommand command = command(conversation, owner, "same-key", HASH);

        GenerationSubmissionResult first = repository.submit(command);
        GenerationSubmissionResult repeated = repository.submit(command);

        assertThat(repeated.deduplicated()).isTrue();
        assertThat(repeated.generation().id()).isEqualTo(first.generation().id());
        assertThat(repeated.userMessage().id()).isEqualTo(first.userMessage().id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_generations", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conversation_messages", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT next_message_sequence FROM conversations WHERE id = ?", Long.class, conversation))
                .isEqualTo(1L);
    }

    @Test
    void sameKeyWithDifferentHashIsRejectedAndActiveConversationIsSerialized() {
        UUID owner = UUID.randomUUID();
        UUID conversation = insertConversation(owner, ConversationStatus.OPEN);
        repository.submit(command(conversation, owner, "same-key", HASH));

        assertThatThrownBy(() -> repository.submit(command(conversation, owner, "same-key", "b".repeat(64))))
                .isInstanceOf(AssistantException.class)
                .extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThatThrownBy(() -> repository.submit(command(conversation, owner, "other-key", "c".repeat(64))))
                .isInstanceOf(AssistantException.class)
                .extracting("code").isEqualTo("GENERATION_ALREADY_ACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_generations", Integer.class)).isEqualTo(1);
    }

    @Test
    void archivedConversationAndOtherOwnerCannotSubmitOrReadEvents() {
        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        UUID conversation = insertConversation(owner, ConversationStatus.ARCHIVED);

        assertThatThrownBy(() -> repository.submit(command(conversation, owner, "archived", HASH)))
                .isInstanceOf(AssistantException.class)
                .extracting("code").isEqualTo("CONVERSATION_NOT_OPEN");
        assertThatThrownBy(() -> repository.submit(command(conversation, otherOwner, "foreign", HASH)))
                .isInstanceOf(AssistantException.class)
                .extracting("code").isEqualTo("CONVERSATION_NOT_FOUND");
    }

    @Test
    void eventCursorReturnsStrictlyOrderedReplayForOwner() {
        UUID owner = UUID.randomUUID();
        UUID conversation = insertConversation(owner, ConversationStatus.OPEN);
        GenerationSubmissionResult result = repository.submit(command(conversation, owner, "events", HASH));
        UUID generationId = result.generation().id();
        Instant expiry = NOW.plusSeconds(60);
        insertEvent(generationId, 1, "DELTA", "{\"delta\":\"hello\"}", expiry);
        insertEvent(generationId, 2, "COMPLETED", "{\"status\":\"COMPLETED\"}", expiry);

        List<AssistantGenerationEvent> replay = repository.findEventsOwned(
                generationId, conversation, owner, 0, 10, NOW);

        assertThat(replay).extracting(AssistantGenerationEvent::sequenceNo).containsExactly(1L, 2L);
        assertThat(replay).extracting(AssistantGenerationEvent::eventType)
                .extracting(Enum::name).containsExactly("DELTA", "COMPLETED");
        insertEvent(generationId, 3, "DELTA", "{\"delta\":\"expired\"}", NOW.minusSeconds(1));
        assertThat(repository.findEventsOwned(generationId, conversation, owner, 2, 10, NOW)).isEmpty();
        assertThatThrownBy(() -> repository.findEventsOwned(
                generationId, conversation, UUID.randomUUID(), -1, 10, NOW))
                .isInstanceOf(AssistantException.class)
                .extracting("code").isEqualTo("GENERATION_NOT_FOUND");
    }

    @Test
    void legacyMigrationHashRemainsReadableAfterEntityMapping() {
        UUID owner = UUID.randomUUID();
        UUID conversation = insertConversation(owner, ConversationStatus.OPEN);
        GenerationSubmissionResult result = repository.submit(command(conversation, owner, "legacy", HASH));
        String legacyHash = "legacy-" + result.generation().id();
        jdbc.update("""
                UPDATE chat_generations
                   SET request_hash = ?, status = 'COMPLETED', active_conversation_id = NULL,
                       completed_at = ?, updated_at = ?
                 WHERE id = ?
                """, legacyHash, Timestamp.from(NOW), Timestamp.from(NOW), result.generation().id());

        assertThat(repository.findOwned(result.generation().id(), conversation, owner))
                .isPresent()
                .get()
                .extracting("requestHash").isEqualTo(legacyHash);
    }

    private GenerationSubmissionCommand command(
            UUID conversationId,
            UUID owner,
            String key,
            String hash
    ) {
        return command(conversationId, owner, key, hash, ToolEvidenceSnapshot.empty());
    }

    private GenerationSubmissionCommand command(
            UUID conversationId,
            UUID owner,
            String key,
            String hash,
            ToolEvidenceSnapshot evidence
    ) {
        return new GenerationSubmissionCommand(
                conversationId,
                owner,
                key,
                hash,
                "How is the crop?",
                evidence.isEmpty()
                        ? ToolEvidenceCollection.skipped("TOOLS_DISABLED")
                        : ToolEvidenceCollection.collected(evidence, 0),
                "none",
                null,
                NOW,
                null,
                NOW.plusSeconds(60)
        );
    }

    private UUID insertConversation(UUID owner, ConversationStatus status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO conversations (
                    id, owner_user_id, title, status, role_snapshot, context_type,
                    next_message_sequence, version, created_at, updated_at,
                    archived_at, purge_after
                ) VALUES (?, ?, 'Test', ?, ?, 'ENTERPRISE', 0, 0, ?, ?, ?, ?)
                """,
                id, owner, status.name(), ROLE_SNAPSHOT,
                Timestamp.from(NOW), Timestamp.from(NOW),
                status == ConversationStatus.ARCHIVED ? Timestamp.from(NOW) : null,
                status == ConversationStatus.ARCHIVED ? Timestamp.from(NOW.plusSeconds(90)) : null);
        return id;
    }

    private void insertEvent(
            UUID generationId,
            long sequence,
            String type,
            String payload,
            Instant expiresAt
    ) {
        jdbc.update("""
                INSERT INTO generation_events (
                    id, generation_id, sequence_no, event_type, payload, created_at, expires_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), generationId, sequence, type, payload,
                Timestamp.from(NOW), Timestamp.from(expiresAt));
    }
}
