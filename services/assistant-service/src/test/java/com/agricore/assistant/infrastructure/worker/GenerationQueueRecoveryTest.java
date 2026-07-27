package com.agricore.assistant.infrastructure.worker;

import com.agricore.assistant.application.port.AssistantRetentionPolicy;
import com.agricore.assistant.application.port.GenerationExecutionRepository;
import com.agricore.assistant.application.port.GenerationWorkDispatcher;
import com.agricore.assistant.infrastructure.configuration.AssistantGenerationWorkerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GenerationQueueRecoveryTest {

    private static final Instant NOW = Instant.parse("2026-07-20T16:00:00Z");

    private final GenerationExecutionRepository repository = mock(GenerationExecutionRepository.class);
    private final GenerationWorkDispatcher dispatcher = mock(GenerationWorkDispatcher.class);
    private final AssistantRetentionPolicy retentionPolicy = mock(AssistantRetentionPolicy.class);
    private final AssistantGenerationWorkerProperties properties = new AssistantGenerationWorkerProperties();
    private final GenerationQueueRecovery recovery = new GenerationQueueRecovery(
            repository,
            dispatcher,
            properties,
            retentionPolicy,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @BeforeEach
    void configureRecovery() {
        properties.setRecoveryBatchSize(2);
        when(retentionPolicy.generationEventRetention()).thenReturn(Duration.ofHours(24));
    }

    @Test
    void expiresLeasesBeforeDispatchingTheOldestBoundedQueueBatch() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(repository.findQueuedGenerationIds(2)).thenReturn(List.of(first, second));

        recovery.recover();

        var order = inOrder(repository, dispatcher);
        order.verify(repository).expireLeases(NOW, NOW.plus(Duration.ofHours(24)), 2);
        order.verify(repository).findQueuedGenerationIds(2);
        order.verify(dispatcher).dispatch(first);
        order.verify(dispatcher).dispatch(second);
    }

    @Test
    void queueRecoveryStillRunsWhenLeaseExpiryFails() {
        UUID queued = UUID.randomUUID();
        when(repository.expireLeases(NOW, NOW.plus(Duration.ofHours(24)), 2))
                .thenThrow(new IllegalStateException("raw database detail"));
        when(repository.findQueuedGenerationIds(2)).thenReturn(List.of(queued));

        recovery.recover();

        verify(dispatcher).dispatch(queued);
    }

    @Test
    void disabledRecoveryDoesNoDatabaseOrDispatchWork() {
        properties.setRecoveryEnabled(false);

        recovery.recover();

        verifyNoInteractions(repository, dispatcher);
        verify(retentionPolicy, never()).generationEventRetention();
    }
}
