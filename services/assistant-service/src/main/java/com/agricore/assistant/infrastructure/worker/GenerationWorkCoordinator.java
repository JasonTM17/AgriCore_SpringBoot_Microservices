package com.agricore.assistant.infrastructure.worker;

import com.agricore.assistant.application.port.GenerationWorkDispatcher;
import com.agricore.assistant.infrastructure.configuration.AssistantGenerationWorkerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.scheduler.Scheduler;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GenerationWorkCoordinator implements GenerationWorkDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerationWorkCoordinator.class);

    private final GenerationWorker worker;
    private final AssistantGenerationWorkerProperties properties;
    private final Scheduler scheduler;
    private final Set<UUID> activeGenerations = ConcurrentHashMap.newKeySet();

    public GenerationWorkCoordinator(
            GenerationWorker worker,
            AssistantGenerationWorkerProperties properties,
            Scheduler assistantGenerationScheduler
    ) {
        this.worker = worker;
        this.properties = properties;
        this.scheduler = assistantGenerationScheduler;
    }

    @Override
    public void dispatchAfterCommit(UUID generationId) {
        Objects.requireNonNull(generationId, "generationId is required");
        if (!properties.isEnabled()) {
            return;
        }
        runAfterCommit(() -> dispatch(generationId));
    }

    public void dispatch(UUID generationId) {
        Objects.requireNonNull(generationId, "generationId is required");
        if (!properties.isEnabled() || !activeGenerations.add(generationId)) {
            return;
        }
        try {
            scheduler.schedule(() -> execute(generationId));
        } catch (RuntimeException error) {
            activeGenerations.remove(generationId);
            logSafeFailure(generationId, error);
        }
    }

    private void execute(UUID generationId) {
        try {
            worker.execute(generationId)
                    .doFinally(signal -> activeGenerations.remove(generationId))
                    .subscribe(
                            ignored -> {
                            },
                            error -> logSafeFailure(generationId, error)
                    );
        } catch (RuntimeException error) {
            activeGenerations.remove(generationId);
            logSafeFailure(generationId, error);
        }
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private void logSafeFailure(UUID generationId, Throwable error) {
        LOGGER.warn(
                "Generation dispatch failed generationId={} errorType={}",
                generationId,
                error.getClass().getSimpleName()
        );
    }
}
