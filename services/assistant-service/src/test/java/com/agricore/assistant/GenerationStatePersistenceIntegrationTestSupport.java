package com.agricore.assistant;

import com.agricore.assistant.application.model.GenerationSubmissionCommand;
import com.agricore.assistant.application.model.GenerationSubmissionResult;
import com.agricore.assistant.application.model.ToolEvidenceCollection;
import com.agricore.assistant.application.port.GenerationExecutionRepository;
import com.agricore.assistant.application.port.GenerationRepository;
import com.agricore.assistant.domain.model.AssistantGenerationEvent;
import com.agricore.assistant.domain.model.ConversationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

abstract class GenerationStatePersistenceIntegrationTestSupport {

    static final Instant NOW = Instant.parse("2026-07-20T05:00:00Z");
    private static final Duration EVENT_RETENTION = Duration.ofHours(1);
    private static final String HASH = "d".repeat(64);
    private static final String ROLE_SNAPSHOT = "[\"FIELD_WORKER\"]";

    @Autowired
    GenerationRepository generationRepository;

    @Autowired
    GenerationExecutionRepository executionRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void cleanGenerationState() {
        jdbc.update("DELETE FROM generation_events");
        jdbc.update("DELETE FROM conversation_messages");
        jdbc.update("DELETE FROM chat_generations");
        jdbc.update("DELETE FROM assistant_audit_events");
        jdbc.update("DELETE FROM conversations");
    }

    List<AssistantGenerationEvent> events(GenerationSubmissionResult submitted, UUID owner) {
        return generationRepository.findEventsOwned(
                submitted.generation().id(), submitted.generation().conversationId(),
                owner, -1, 100, at(10));
    }

    GenerationSubmissionResult submit(
            UUID conversationId,
            UUID owner,
            String key,
            Instant submittedAt
    ) {
        return generationRepository.submit(new GenerationSubmissionCommand(
                conversationId,
                owner,
                key,
                HASH,
                "How is the crop?",
                ToolEvidenceCollection.skipped("TOOLS_DISABLED"),
                "openai",
                "gpt-test",
                submittedAt,
                submittedAt.plus(Duration.ofDays(30)),
                submittedAt.plus(EVENT_RETENTION)
        ));
    }

    UUID insertConversation(UUID owner) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO conversations (
                    id, owner_user_id, title, status, role_snapshot, context_type,
                    next_message_sequence, version, created_at, updated_at
                ) VALUES (?, ?, 'Test', ?, ?, 'ENTERPRISE', 0, 0, ?, ?)
                """,
                id, owner, ConversationStatus.OPEN.name(), ROLE_SNAPSHOT,
                Timestamp.from(NOW), Timestamp.from(NOW));
        return id;
    }

    static Instant at(long seconds) {
        return NOW.plusSeconds(seconds);
    }

    static Instant expiresAt(long seconds) {
        return at(seconds).plus(EVENT_RETENTION);
    }
}
