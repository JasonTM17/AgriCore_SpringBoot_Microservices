package com.agricore.assistant.infrastructure.worker;

import com.agricore.assistant.application.port.AssistantRetentionPolicy;
import com.agricore.assistant.application.port.GenerationExecutionRepository;
import com.agricore.assistant.infrastructure.configuration.AssistantGenerationWorkerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
public class GenerationQueueRecovery {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerationQueueRecovery.class);

    private final GenerationExecutionRepository repository;
    private final GenerationWorkCoordinator coordinator;
    private final AssistantGenerationWorkerProperties properties;
    private final AssistantRetentionPolicy retentionPolicy;
    private final Clock clock;

    public GenerationQueueRecovery(
            GenerationExecutionRepository repository,
            GenerationWorkCoordinator coordinator,
            AssistantGenerationWorkerProperties properties,
            AssistantRetentionPolicy retentionPolicy,
            Clock clock
    ) {
        this.repository = repository;
        this.coordinator = coordinator;
        this.properties = properties;
        this.retentionPolicy = retentionPolicy;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        recover();
    }

    @Scheduled(fixedDelayString = "${agricore.assistant.worker.recovery-interval:PT5S}")
    public void recover() {
        if (!properties.isEnabled() || !properties.isRecoveryEnabled()) {
            return;
        }
        expireAbandonedWork();
        dispatchQueuedWork();
    }

    private void expireAbandonedWork() {
        Instant now = clock.instant();
        try {
            repository.expireLeases(
                    now,
                    now.plus(retentionPolicy.generationEventRetention()),
                    properties.getRecoveryBatchSize()
            );
        } catch (RuntimeException error) {
            logSafeFailure("lease-expiry", error);
        }
    }

    private void dispatchQueuedWork() {
        try {
            for (UUID generationId : repository.findQueuedGenerationIds(
                    properties.getRecoveryBatchSize())) {
                coordinator.dispatch(generationId);
            }
        } catch (RuntimeException error) {
            logSafeFailure("queue-dispatch", error);
        }
    }

    private void logSafeFailure(String operation, Throwable error) {
        LOGGER.warn(
                "Generation recovery failed operation={} errorType={}",
                operation,
                error.getClass().getSimpleName()
        );
    }
}
