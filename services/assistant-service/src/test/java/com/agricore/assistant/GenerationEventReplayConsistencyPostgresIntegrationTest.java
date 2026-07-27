package com.agricore.assistant;

import com.agricore.assistant.application.model.GenerationEventReplayBatch;
import com.agricore.assistant.application.model.GenerationSubmissionCommand;
import com.agricore.assistant.application.model.GenerationSubmissionResult;
import com.agricore.assistant.application.model.ToolEvidenceCollection;
import com.agricore.assistant.application.port.GenerationEventReplayRepository;
import com.agricore.assistant.application.port.GenerationRepository;
import com.agricore.assistant.infrastructure.persistence.GenerationCancellationTransitionStore;
import com.agricore.assistant.infrastructure.persistence.entity.ChatGenerationEntity;
import com.agricore.assistant.infrastructure.persistence.repository.ChatGenerationJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class GenerationEventReplayConsistencyPostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-22T03:00:00Z");
    private static final String ROLE_SNAPSHOT = "[\"FIELD_WORKER\"]";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private GenerationRepository generationRepository;

    @Autowired
    private GenerationEventReplayRepository replayRepository;

    @Autowired
    private ChatGenerationJpaRepository chatGenerationRepository;

    @Autowired
    private GenerationCancellationTransitionStore cancellationStore;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private TransactionTemplate transactionTemplate;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        jdbc.update("DELETE FROM generation_events");
        jdbc.update("DELETE FROM conversation_messages");
        jdbc.update("DELETE FROM chat_generations");
        jdbc.update("DELETE FROM conversations");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM generation_events");
        jdbc.update("DELETE FROM conversation_messages");
        jdbc.update("DELETE FROM chat_generations");
        jdbc.update("DELETE FROM conversations");
    }

    @Test
    void replaySnapshotHoldsGenerationLockWhileReadingEvents() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID conversationId = insertConversation(owner);
        GenerationSubmissionResult submission = generationRepository.submit(new GenerationSubmissionCommand(
                conversationId,
                owner,
                "replay-lock",
                "a".repeat(64),
                "Replay safely",
                ToolEvidenceCollection.skipped("TOOLS_DISABLED"),
                "none",
                null,
                NOW,
                null,
                NOW.plusSeconds(60)
        ));
        UUID generationId = submission.generation().id();
        CountDownLatch replayLocked = new CountDownLatch(1);
        CountDownLatch writerStarted = new CountDownLatch(1);
        CountDownLatch releaseReplay = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<GenerationEventReplayBatch> replay = executor.submit(() -> transactionTemplate.execute(status -> {
                ChatGenerationEntity generation = chatGenerationRepository.findOwnedForReplay(
                                generationId, conversationId, owner)
                        .orElseThrow();
                assertThat(generation.getNextEventSequence()).isEqualTo(1L);
                replayLocked.countDown();
                awaitLatch(writerStarted);
                awaitLatch(releaseReplay);
                return replayRepository.findOwned(
                                generationId, conversationId, owner, -1, 10, NOW)
                        .orElseThrow();
            }));

            assertThat(replayLocked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> writer = executor.submit(() -> {
                writerStarted.countDown();
                return cancellationStore.request(
                        generationId, conversationId, owner, NOW.plusSeconds(1), NOW.plusSeconds(60));
            });

            Thread.sleep(250);
            assertThat(writer).isNotDone();
            releaseReplay.countDown();

            GenerationEventReplayBatch batch = replay.get(5, TimeUnit.SECONDS);
            assertThat(batch.events()).extracting(event -> event.sequenceNo()).containsExactly(0L);
            writer.get(5, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM generation_events WHERE generation_id = ?",
                    Integer.class,
                    generationId)).isEqualTo(2);
        } finally {
            releaseReplay.countDown();
            executor.shutdownNow();
        }
    }

    private UUID insertConversation(UUID owner) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO conversations (
                    id, owner_user_id, title, status, role_snapshot, context_type,
                    next_message_sequence, version, created_at, updated_at, archived_at, purge_after
                ) VALUES (?, ?, 'Replay test', 'OPEN', ?, 'ENTERPRISE', 0, 0, ?, ?, NULL, NULL)
                """, id, owner, ROLE_SNAPSHOT, Timestamp.from(NOW), Timestamp.from(NOW));
        return id;
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while coordinating replay consistency test", exception);
        }
    }
}
