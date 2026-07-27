package com.agricore.assistant;

import com.agricore.assistant.application.model.DeltaAppendResult;
import com.agricore.assistant.application.model.GenerationSubmissionResult;
import com.agricore.assistant.application.model.GenerationLeaseStatus;
import com.agricore.assistant.domain.exception.AssistantException;
import com.agricore.assistant.domain.model.AssistantGenerationEvent;
import com.agricore.assistant.domain.model.GenerationEventType;
import com.agricore.assistant.domain.model.GenerationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class GenerationTerminalPersistenceIntegrationTest
        extends GenerationStatePersistenceIntegrationTestSupport {

    @Test
    void failureReleasesSlotAndNeverPersistsRawProviderError() {
        UUID owner = UUID.randomUUID();
        UUID conversationId = insertConversation(owner);
        GenerationSubmissionResult submitted = submit(conversationId, owner, "failure", NOW);
        UUID leaseToken = UUID.randomUUID();
        executionRepository.claim(submitted.generation().id(), leaseToken, at(1), at(31), expiresAt(1));

        var failed = executionRepository.fail(
                submitted.generation().id(), leaseToken,
                "provider exposed api-key sk-secret", at(4), expiresAt(4)).orElseThrow();

        assertThat(failed.status()).isEqualTo(GenerationStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo("GENERATION_FAILED");
        assertThat(failed.activeConversationId()).isNull();
        assertThat(failed.providerLatencyMs()).isEqualTo(3_000L);
        assertThat(failed.totalLatencyMs()).isEqualTo(4_000L);
        String errorPayload = jdbc.queryForObject(
                "SELECT payload FROM generation_events WHERE generation_id = ? AND event_type = 'ERROR'",
                String.class, submitted.generation().id());
        assertThat(errorPayload).contains("GENERATION_FAILED").doesNotContain("sk-secret", "api-key");
        assertThat(executionRepository.fail(
                submitted.generation().id(), leaseToken, "OTHER", at(5), expiresAt(5))).isEmpty();
        assertThat(submit(conversationId, owner, "after-failure", at(6)).generation().status())
                .isEqualTo(GenerationStatus.QUEUED);
    }

    @Test
    void queuedCancellationIsOwnedTerminalAndIdempotent() {
        UUID owner = UUID.randomUUID();
        UUID conversationId = insertConversation(owner);
        GenerationSubmissionResult submitted = submit(conversationId, owner, "queued-cancel", NOW);

        assertThatThrownBy(() -> executionRepository.requestCancellation(
                submitted.generation().id(), conversationId, UUID.randomUUID(), at(1), expiresAt(1)))
                .isInstanceOf(AssistantException.class)
                .extracting("code").isEqualTo("GENERATION_NOT_FOUND");
        var cancelled = executionRepository.requestCancellation(
                submitted.generation().id(), conversationId, owner, at(2), expiresAt(2));

        assertThat(cancelled.changed()).isTrue();
        assertThat(cancelled.workerCancellationRequired()).isFalse();
        assertThat(cancelled.generation().status()).isEqualTo(GenerationStatus.CANCELLED);
        assertThat(cancelled.generation().activeConversationId()).isNull();
        assertThat(cancelled.generation().totalLatencyMs()).isEqualTo(2_000L);
        var repeated = executionRepository.requestCancellation(
                submitted.generation().id(), conversationId, owner, at(3), expiresAt(3));
        assertThat(repeated.changed()).isFalse();
        assertThat(repeated.workerCancellationRequired()).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM generation_events WHERE generation_id = ?",
                Integer.class, submitted.generation().id())).isEqualTo(2);
        assertThat(events(submitted, owner)).extracting(AssistantGenerationEvent::eventType)
                .containsExactly(GenerationEventType.STATUS, GenerationEventType.CANCELLED);
        assertThat(submit(conversationId, owner, "after-queued-cancel", at(4)).generation().status())
                .isEqualTo(GenerationStatus.QUEUED);
    }

    @Test
    void runningCancellationSignalsWorkerAndFinishesWithMatchingLease() {
        UUID owner = UUID.randomUUID();
        UUID conversationId = insertConversation(owner);
        GenerationSubmissionResult submitted = submit(conversationId, owner, "running-cancel", NOW);
        UUID leaseToken = UUID.randomUUID();
        executionRepository.claim(submitted.generation().id(), leaseToken, at(1), at(31), expiresAt(1));

        var requested = executionRepository.requestCancellation(
                submitted.generation().id(), conversationId, owner, at(2), expiresAt(2));
        assertThat(requested.changed()).isTrue();
        assertThat(requested.workerCancellationRequired()).isTrue();
        assertThat(requested.generation().status()).isEqualTo(GenerationStatus.CANCEL_REQUESTED);
        assertThat(executionRepository.appendDelta(
                submitted.generation().id(), leaseToken, "late", at(3), at(33), expiresAt(3)))
                .isEqualTo(DeltaAppendResult.CANCEL_REQUESTED);
        var repeated = executionRepository.requestCancellation(
                submitted.generation().id(), conversationId, owner, at(4), expiresAt(4));
        assertThat(repeated.changed()).isFalse();
        assertThat(repeated.workerCancellationRequired()).isTrue();
        assertThat(repeated.generation().cancelRequestedAt()).isEqualTo(at(2));
        assertThat(executionRepository.finishCancellation(
                submitted.generation().id(), UUID.randomUUID(), at(5), expiresAt(5))).isEmpty();

        var cancelled = executionRepository.finishCancellation(
                submitted.generation().id(), leaseToken, at(5), expiresAt(5)).orElseThrow();
        assertThat(cancelled.status()).isEqualTo(GenerationStatus.CANCELLED);
        assertThat(cancelled.activeConversationId()).isNull();
        assertThat(cancelled.providerLatencyMs()).isEqualTo(4_000L);
        assertThat(cancelled.totalLatencyMs()).isEqualTo(5_000L);
        assertThat(executionRepository.finishCancellation(
                submitted.generation().id(), leaseToken, at(6), expiresAt(6))).isEmpty();
        assertThat(events(submitted, owner)).extracting(AssistantGenerationEvent::eventType)
                .containsExactly(GenerationEventType.STATUS, GenerationEventType.STATUS,
                        GenerationEventType.STATUS, GenerationEventType.CANCELLED);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM conversation_messages WHERE generation_id = ?",
                Integer.class, submitted.generation().id())).isEqualTo(1);
    }

    @Test
    void heartbeatRenewsOnlyTheActiveLeaseAndSurfacesCancellation() {
        UUID owner = UUID.randomUUID();
        UUID conversationId = insertConversation(owner);
        GenerationSubmissionResult submitted = submit(conversationId, owner, "heartbeat", NOW);
        UUID leaseToken = UUID.randomUUID();
        executionRepository.claim(submitted.generation().id(), leaseToken, at(1), at(31), expiresAt(1));

        assertThat(executionRepository.renewLease(
                submitted.generation().id(), UUID.randomUUID(), at(2), at(62)))
                .isEqualTo(GenerationLeaseStatus.STALE);
        assertThat(executionRepository.renewLease(
                submitted.generation().id(), leaseToken, at(2), at(62)))
                .isEqualTo(GenerationLeaseStatus.ACTIVE);
        var renewed = generationRepository.findOwned(
                submitted.generation().id(), conversationId, owner).orElseThrow();
        assertThat(renewed.leaseExpiresAt()).isEqualTo(at(62));
        assertThat(renewed.updatedAt()).isEqualTo(at(2));

        executionRepository.requestCancellation(
                submitted.generation().id(), conversationId, owner, at(3), expiresAt(3));
        assertThat(executionRepository.renewLease(
                submitted.generation().id(), leaseToken, at(4), at(64)))
                .isEqualTo(GenerationLeaseStatus.CANCEL_REQUESTED);
        assertThat(generationRepository.findOwned(
                submitted.generation().id(), conversationId, owner).orElseThrow().leaseExpiresAt())
                .isEqualTo(at(62));

        executionRepository.finishCancellation(
                submitted.generation().id(), leaseToken, at(5), expiresAt(5));
        assertThat(executionRepository.renewLease(
                submitted.generation().id(), leaseToken, at(6), at(66)))
                .isEqualTo(GenerationLeaseStatus.STALE);
    }

    @Test
    void queuedRecoveryIsOldestFirstAndStrictlyBounded() {
        UUID owner = UUID.randomUUID();
        UUID laterConversation = insertConversation(owner);
        UUID earlierConversation = insertConversation(owner);
        GenerationSubmissionResult later = submit(laterConversation, owner, "later", at(2));
        GenerationSubmissionResult earlier = submit(earlierConversation, owner, "earlier", NOW);

        assertThat(executionRepository.findQueuedGenerationIds(1)).containsExactly(earlier.generation().id());
        assertThat(executionRepository.findQueuedGenerationIds(2))
                .containsExactly(earlier.generation().id(), later.generation().id());
        assertThatIllegalArgumentException().isThrownBy(() -> executionRepository.findQueuedGenerationIds(0));
        assertThatIllegalArgumentException().isThrownBy(() -> executionRepository.findQueuedGenerationIds(1_001));
    }
}
