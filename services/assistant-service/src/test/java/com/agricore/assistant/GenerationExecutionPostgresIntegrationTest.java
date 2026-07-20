package com.agricore.assistant;

import com.agricore.assistant.application.model.DeltaAppendResult;
import com.agricore.assistant.application.model.GenerationCompletion;
import com.agricore.assistant.application.model.GenerationSubmissionResult;
import com.agricore.assistant.domain.model.AssistantGenerationEvent;
import com.agricore.assistant.domain.model.AssistantMessage;
import com.agricore.assistant.domain.model.GenerationEventType;
import com.agricore.assistant.domain.model.GenerationStatus;
import com.agricore.assistant.domain.model.MessageRole;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class GenerationExecutionPostgresIntegrationTest
        extends GenerationStatePersistenceIntegrationTestSupport {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("agricore_assistant")
            .withUsername("agricore")
            .withPassword("agricore_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test
    void claimLoadsChronologicalHistoryAndRejectsSecondLease() {
        UUID owner = UUID.randomUUID();
        UUID conversationId = insertConversation(owner);
        GenerationSubmissionResult first = submit(conversationId, owner, "first", NOW);
        UUID firstLease = UUID.randomUUID();

        assertThat(executionRepository.claim(
                first.generation().id(), firstLease, at(1), at(31), expiresAt(1))).isPresent();
        assertThat(executionRepository.appendDelta(
                first.generation().id(), firstLease, "First answer", at(2), at(32), expiresAt(2)))
                .isEqualTo(DeltaAppendResult.APPENDED);
        assertThat(executionRepository.complete(
                first.generation().id(), firstLease,
                new GenerationCompletion("First answer", "stop", 8, 2, at(3), expiresAt(3))))
                .isPresent();

        GenerationSubmissionResult second = submit(conversationId, owner, "second", at(4));
        assertThat(executionRepository.findQueuedGenerationIds(10)).contains(second.generation().id());
        UUID secondLease = UUID.randomUUID();
        var context = executionRepository.claim(
                second.generation().id(), secondLease, at(5), at(35), expiresAt(5)).orElseThrow();

        assertThat(context.generation().status()).isEqualTo(GenerationStatus.RUNNING);
        assertThat(context.generation().leaseToken()).isEqualTo(secondLease);
        assertThat(context.generation().attemptCount()).isEqualTo(1);
        assertThat(context.messages())
                .extracting(AssistantMessage::sequenceNo)
                .containsExactly(0L, 1L, 2L);
        assertThat(context.messages())
                .extracting(AssistantMessage::role)
                .containsExactly(MessageRole.USER, MessageRole.ASSISTANT, MessageRole.USER);
        assertThat(context.messages())
                .extracting(AssistantMessage::content)
                .containsExactly("How is the crop?", "First answer", "How is the crop?");
        assertThat(executionRepository.claim(
                second.generation().id(), UUID.randomUUID(), at(6), at(36), expiresAt(6))).isEmpty();
        assertThat(executionRepository.findQueuedGenerationIds(10)).doesNotContain(second.generation().id());
    }

    @Test
    void concurrentClaimsHaveExactlyOneLeaseOwner() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID conversationId = insertConversation(owner);
        GenerationSubmissionResult submitted = submit(conversationId, owner, "claim-race", NOW);
        CountDownLatch workersReady = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                workersReady.countDown();
                start.await();
                return executionRepository.claim(
                        submitted.generation().id(), UUID.randomUUID(), at(1), at(31), expiresAt(1));
            });
            var second = executor.submit(() -> {
                workersReady.countDown();
                start.await();
                return executionRepository.claim(
                        submitted.generation().id(), UUID.randomUUID(), at(1), at(31), expiresAt(1));
            });
            assertThat(workersReady.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    first.get(10, TimeUnit.SECONDS).isPresent(),
                    second.get(10, TimeUnit.SECONDS).isPresent()
            )).containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject(
                "SELECT attempt_count FROM chat_generations WHERE id = ?",
                Integer.class, submitted.generation().id())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM generation_events WHERE generation_id = ?",
                Integer.class, submitted.generation().id())).isEqualTo(2);
    }

    @Test
    void deltaAndCompletionPersistMetricsEscapedReplayAndFinalMessage() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID conversationId = insertConversation(owner);
        GenerationSubmissionResult submitted = submit(conversationId, owner, "complete", NOW);
        UUID leaseToken = UUID.randomUUID();
        executionRepository.claim(submitted.generation().id(), leaseToken, at(1), at(31), expiresAt(1));

        assertThat(executionRepository.appendDelta(
                submitted.generation().id(), UUID.randomUUID(), "stale", at(2), at(32), expiresAt(2)))
                .isEqualTo(DeltaAppendResult.STALE);
        String delta = "He said \"healthy\"\n";
        assertThat(executionRepository.appendDelta(
                submitted.generation().id(), leaseToken, delta, at(2), at(62), expiresAt(2)))
                .isEqualTo(DeltaAppendResult.APPENDED);
        var running = generationRepository.findOwned(
                submitted.generation().id(), conversationId, owner).orElseThrow();
        assertThat(running.firstTokenAt()).isEqualTo(at(2));
        assertThat(running.firstTokenLatencyMs()).isEqualTo(1_000L);
        assertThat(running.leaseExpiresAt()).isEqualTo(at(62));

        var completed = executionRepository.complete(
                submitted.generation().id(), leaseToken,
                new GenerationCompletion("The crop is healthy.", "STOP", 11, 4, at(5), expiresAt(5)))
                .orElseThrow();

        assertThat(completed.status()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(completed.activeConversationId()).isNull();
        assertThat(completed.leaseToken()).isNull();
        assertThat(completed.inputTokens()).isEqualTo(11L);
        assertThat(completed.outputTokens()).isEqualTo(4L);
        assertThat(completed.firstTokenLatencyMs()).isEqualTo(1_000L);
        assertThat(completed.providerLatencyMs()).isEqualTo(4_000L);
        assertThat(completed.totalLatencyMs()).isEqualTo(5_000L);

        List<AssistantGenerationEvent> events = events(submitted, owner);
        assertThat(events).extracting(AssistantGenerationEvent::sequenceNo)
                .containsExactly(0L, 1L, 2L, 3L);
        assertThat(events).extracting(AssistantGenerationEvent::eventType)
                .containsExactly(GenerationEventType.STATUS, GenerationEventType.STATUS,
                        GenerationEventType.DELTA, GenerationEventType.COMPLETED);
        assertThat(objectMapper.readTree(events.get(2).payload()).get("delta").asText()).isEqualTo(delta);
        var completionPayload = objectMapper.readTree(events.get(3).payload());
        assertThat(completionPayload.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(completionPayload.get("finishReason").asText()).isEqualTo("stop");

        assertThat(jdbc.queryForObject(
                "SELECT content FROM conversation_messages WHERE generation_id = ? AND role = 'ASSISTANT'",
                String.class, submitted.generation().id())).isEqualTo("The crop is healthy.");
        assertThat(jdbc.queryForObject(
                "SELECT next_message_sequence FROM conversations WHERE id = ?",
                Long.class, conversationId)).isEqualTo(2L);
        assertThat(executionRepository.complete(
                submitted.generation().id(), leaseToken,
                new GenerationCompletion("duplicate", "stop", null, null, at(6), expiresAt(6))))
                .isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM conversation_messages WHERE generation_id = ? AND role = 'ASSISTANT'",
                Integer.class, submitted.generation().id())).isEqualTo(1);
        assertThat(submit(conversationId, owner, "after-complete", at(7)).generation().status())
                .isEqualTo(GenerationStatus.QUEUED);
    }

    @Test
    void completionPersistenceFailureRollsBackTerminalStateAndEvent() {
        UUID owner = UUID.randomUUID();
        UUID conversationId = insertConversation(owner);
        GenerationSubmissionResult submitted = submit(conversationId, owner, "rollback", NOW);
        UUID leaseToken = UUID.randomUUID();
        executionRepository.claim(submitted.generation().id(), leaseToken, at(1), at(31), expiresAt(1));
        jdbc.update("""
                INSERT INTO conversation_messages (
                    id, conversation_id, generation_id, sequence_no,
                    role, content, token_count, created_at
                ) VALUES (?, ?, ?, 1, 'ASSISTANT', 'pre-existing', 1, ?)
                """,
                UUID.randomUUID(), conversationId, submitted.generation().id(), Timestamp.from(at(2)));
        jdbc.update("UPDATE conversations SET next_message_sequence = 2 WHERE id = ?", conversationId);

        assertThatThrownBy(() -> executionRepository.complete(
                submitted.generation().id(), leaseToken,
                new GenerationCompletion("new answer", "stop", 2, 2, at(3), expiresAt(3))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject(
                "SELECT status FROM chat_generations WHERE id = ?",
                String.class, submitted.generation().id())).isEqualTo("RUNNING");
        assertThat(jdbc.queryForObject(
                "SELECT active_conversation_id FROM chat_generations WHERE id = ?",
                UUID.class, submitted.generation().id())).isEqualTo(conversationId);
        assertThat(jdbc.queryForObject(
                "SELECT next_event_sequence FROM chat_generations WHERE id = ?",
                Long.class, submitted.generation().id())).isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM generation_events WHERE generation_id = ? AND event_type = 'COMPLETED'",
                Integer.class, submitted.generation().id())).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT next_message_sequence FROM conversations WHERE id = ?",
                Long.class, conversationId)).isEqualTo(2L);
    }

}
