package com.agricore.assistant;

import com.agricore.assistant.application.model.GenerationSubmissionResult;
import com.agricore.assistant.domain.model.AssistantGenerationEvent;
import com.agricore.assistant.domain.model.GenerationEventType;
import com.agricore.assistant.domain.model.GenerationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class GenerationLeaseExpiryPersistenceIntegrationTest
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
    void expiresAbandonedWorkWithoutRetryingOrLeavingConversationSlotsLocked() {
        UUID owner = UUID.randomUUID();
        UUID runningConversation = insertConversation(owner);
        UUID cancellingConversation = insertConversation(owner);
        GenerationSubmissionResult running = submit(runningConversation, owner, "expired-running", NOW);
        GenerationSubmissionResult cancelling = submit(cancellingConversation, owner, "expired-cancel", NOW);
        UUID runningLease = UUID.randomUUID();
        UUID cancellingLease = UUID.randomUUID();
        executionRepository.claim(running.generation().id(), runningLease, at(1), at(11), expiresAt(1));
        executionRepository.claim(cancelling.generation().id(), cancellingLease, at(2), at(22), expiresAt(2));
        executionRepository.requestCancellation(
                cancelling.generation().id(), cancellingConversation, owner, at(3), expiresAt(3));

        assertThat(executionRepository.expireLeases(at(10), expiresAt(10), 10)).isZero();
        assertThat(executionRepository.expireLeases(at(15), expiresAt(15), 1)).isEqualTo(1);
        var failed = generationRepository.findOwned(
                running.generation().id(), runningConversation, owner).orElseThrow();
        assertThat(failed.status()).isEqualTo(GenerationStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo("GENERATION_WORKER_LOST");
        assertThat(failed.activeConversationId()).isNull();
        assertThat(events(running, owner)).extracting(AssistantGenerationEvent::eventType)
                .containsExactly(GenerationEventType.STATUS, GenerationEventType.STATUS, GenerationEventType.ERROR);

        assertThat(executionRepository.expireLeases(at(23), expiresAt(23), 10)).isEqualTo(1);
        var cancelled = generationRepository.findOwned(
                cancelling.generation().id(), cancellingConversation, owner).orElseThrow();
        assertThat(cancelled.status()).isEqualTo(GenerationStatus.CANCELLED);
        assertThat(cancelled.errorCode()).isNull();
        assertThat(cancelled.cancelledAt()).isEqualTo(at(23));
        assertThat(events(cancelling, owner)).extracting(AssistantGenerationEvent::eventType)
                .containsExactly(
                        GenerationEventType.STATUS,
                        GenerationEventType.STATUS,
                        GenerationEventType.STATUS,
                        GenerationEventType.CANCELLED
                );
        assertThat(submit(runningConversation, owner, "after-expiry", at(24)).generation().status())
                .isEqualTo(GenerationStatus.QUEUED);
    }

    @Test
    void rejectsUnboundedExpiryBatches() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> executionRepository.expireLeases(at(1), expiresAt(1), 0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> executionRepository.expireLeases(at(1), expiresAt(1), 1_001));
    }
}
