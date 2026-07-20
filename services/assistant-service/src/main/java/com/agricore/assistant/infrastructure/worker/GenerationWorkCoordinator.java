package com.agricore.assistant.infrastructure.worker;

import com.agricore.assistant.application.port.GenerationWorkDispatcher;
import com.agricore.assistant.infrastructure.configuration.AssistantGenerationWorkerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class GenerationWorkCoordinator implements GenerationWorkDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerationWorkCoordinator.class);

    private final GenerationWorker worker;
    private final AssistantGenerationWorkerProperties properties;
    private final Scheduler scheduler;
    private final ConcurrentMap<UUID, GenerationTask> activeGenerations = new ConcurrentHashMap<>();

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

    @Override
    public void cancelAfterCommit(UUID generationId) {
        Objects.requireNonNull(generationId, "generationId is required");
        if (!properties.isEnabled()) {
            return;
        }
        runAfterCommit(() -> signalCancellation(generationId));
    }

    @Override
    public void dispatch(UUID generationId) {
        Objects.requireNonNull(generationId, "generationId is required");
        GenerationTask task = new GenerationTask();
        if (!properties.isEnabled() || activeGenerations.putIfAbsent(generationId, task) != null) {
            return;
        }
        try {
            scheduler.schedule(() -> execute(generationId, task));
        } catch (RuntimeException error) {
            activeGenerations.remove(generationId, task);
            logSafeFailure(generationId, error);
        }
    }

    private void execute(UUID generationId, GenerationTask task) {
        try {
            worker.execute(generationId, task.cancellationSignal())
                    .doFinally(signal -> activeGenerations.remove(generationId, task))
                    .subscribe(
                            ignored -> {
                            },
                            error -> logSafeFailure(generationId, error)
                    );
        } catch (RuntimeException error) {
            activeGenerations.remove(generationId, task);
            logSafeFailure(generationId, error);
        }
    }

    private void signalCancellation(UUID generationId) {
        GenerationTask task = activeGenerations.get(generationId);
        if (task != null) {
            task.cancel();
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

    private static final class GenerationTask {

        private final Sinks.Empty<Void> cancellation = Sinks.empty();

        Mono<Void> cancellationSignal() {
            return cancellation.asMono();
        }

        void cancel() {
            cancellation.tryEmitEmpty();
        }
    }
}
