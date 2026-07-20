package com.agricore.assistant;

import com.agricore.assistant.application.model.CreateConversationCommand;
import com.agricore.assistant.application.model.PageQuery;
import com.agricore.assistant.application.port.ConversationContextAccess;
import com.agricore.assistant.application.service.ConversationApplicationService;
import com.agricore.assistant.domain.exception.AssistantException;
import com.agricore.assistant.domain.model.AssistantActor;
import com.agricore.assistant.domain.model.AssistantConversation;
import com.agricore.assistant.domain.model.AssistantMessage;
import com.agricore.assistant.domain.model.ConversationContextType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ConversationMessageHistoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-20T02:00:00Z");

    @Autowired
    private ConversationApplicationService service;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private ConversationContextAccess contextAccess;
    @MockitoBean
    private Clock clock;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM assistant_audit_events");
        jdbc.update("DELETE FROM conversations");
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void historyIsOwnerScopedChronologicalAndPaged() {
        AssistantActor owner = actor();
        AssistantConversation conversation = service.create(
                owner,
                new CreateConversationCommand("History", ConversationContextType.ENTERPRISE, null)
        );
        seedMessages(conversation, owner.subject());

        var firstPage = service.messages(owner, conversation.id(), new PageQuery(0, 2));
        var secondPage = service.messages(owner, conversation.id(), new PageQuery(1, 2));

        assertThat(firstPage.content()).extracting(AssistantMessage::content)
                .containsExactly("first question", "first answer");
        assertThat(firstPage.content()).extracting(AssistantMessage::sequenceNo)
                .containsExactly(0L, 1L);
        assertThat(secondPage.content()).extracting(AssistantMessage::content)
                .containsExactly("second question", "second answer");
        assertThat(firstPage.totalElements()).isEqualTo(4);
        assertThat(firstPage.totalPages()).isEqualTo(2);
    }

    @Test
    void archivedConversationHistoryRemainsReadableToOwner() {
        AssistantActor owner = actor();
        AssistantConversation conversation = service.create(
                owner,
                new CreateConversationCommand("Archived history", ConversationContextType.ENTERPRISE, null)
        );
        seedMessages(conversation, owner.subject());
        service.archive(owner, conversation.id());

        assertThat(service.messages(owner, conversation.id(), new PageQuery(0, 20)).content())
                .hasSize(4);
    }

    @Test
    void otherOwnerCannotUseKnownConversationIdToReadHistory() {
        AssistantActor owner = actor();
        AssistantConversation conversation = service.create(
                owner,
                new CreateConversationCommand("Private history", ConversationContextType.ENTERPRISE, null)
        );
        seedMessages(conversation, owner.subject());

        assertThatThrownBy(() -> service.messages(
                new AssistantActor(UUID.randomUUID(), List.of("FIELD_WORKER")),
                conversation.id(),
                new PageQuery(0, 20)
        )).isInstanceOf(AssistantException.class)
                .extracting("code").isEqualTo("CONVERSATION_NOT_FOUND");
    }

    private void seedMessages(AssistantConversation conversation, UUID ownerId) {
        UUID firstGeneration = insertGeneration(conversation, ownerId, "first");
        UUID secondGeneration = insertGeneration(conversation, ownerId, "second");
        insertMessage(conversation.id(), firstGeneration, 0, "USER", "first question");
        insertMessage(conversation.id(), firstGeneration, 1, "ASSISTANT", "first answer");
        insertMessage(conversation.id(), secondGeneration, 2, "USER", "second question");
        insertMessage(conversation.id(), secondGeneration, 3, "ASSISTANT", "second answer");
        jdbc.update("UPDATE conversations SET next_message_sequence = 4 WHERE id = ?", conversation.id());
    }

    private UUID insertGeneration(AssistantConversation conversation, UUID ownerId, String key) {
        UUID generationId = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO chat_generations (
                            id, conversation_id, owner_user_id, idempotency_key, request_hash,
                            status, role_snapshot, provider, created_at, updated_at, queued_at
                        ) VALUES (?, ?, ?, ?, ?, 'COMPLETED', ?, 'test', ?, ?, ?)
                        """,
                generationId, conversation.id(), ownerId, key, "b".repeat(64),
                "[\"FARM_MANAGER\"]", Timestamp.from(NOW), Timestamp.from(NOW), Timestamp.from(NOW));
        return generationId;
    }

    private void insertMessage(
            UUID conversationId,
            UUID generationId,
            long sequence,
            String role,
            String content
    ) {
        jdbc.update("""
                        INSERT INTO conversation_messages (
                            id, conversation_id, generation_id, sequence_no, role, content, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(), conversationId, generationId, sequence, role, content, Timestamp.from(NOW));
    }

    private AssistantActor actor() {
        return new AssistantActor(UUID.randomUUID(), List.of("FARM_MANAGER"));
    }
}
