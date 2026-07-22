package com.agricore.assistant.infrastructure.worker;

import com.agricore.assistant.application.model.ChatChunk;
import com.agricore.assistant.application.model.DeltaAppendResult;
import com.agricore.assistant.application.model.OutputSafetyAssessment;
import com.agricore.assistant.application.model.ToolEvidenceSnapshot;
import com.agricore.assistant.application.port.AssistantOutputSafetyPolicy;
import com.agricore.assistant.application.port.AssistantRetentionPolicy;
import com.agricore.assistant.application.port.GenerationExecutionRepository;
import com.agricore.assistant.infrastructure.configuration.AssistantGenerationWorkerProperties;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

@Component
public class GenerationOutputCoordinator {

    private static final int MAX_PERSISTED_DELTA_CHARACTERS = 8_000;

    private final GenerationExecutionRepository repository;
    private final AssistantOutputSafetyPolicy safetyPolicy;
    private final AssistantRetentionPolicy retentionPolicy;
    private final AssistantGenerationWorkerProperties properties;
    private final GenerationTerminalCoordinator terminalCoordinator;
    private final Clock clock;
    private final Scheduler scheduler;

    public GenerationOutputCoordinator(
            GenerationExecutionRepository repository,
            AssistantOutputSafetyPolicy safetyPolicy,
            AssistantRetentionPolicy retentionPolicy,
            AssistantGenerationWorkerProperties properties,
            GenerationTerminalCoordinator terminalCoordinator,
            Clock clock,
            Scheduler assistantGenerationScheduler
    ) {
        this.repository = repository;
        this.safetyPolicy = safetyPolicy;
        this.retentionPolicy = retentionPolicy;
        this.properties = properties;
        this.terminalCoordinator = terminalCoordinator;
        this.clock = clock;
        this.scheduler = assistantGenerationScheduler;
    }

    Mono<Void> processBatch(
            UUID generationId,
            UUID leaseToken,
            ToolEvidenceSnapshot evidence,
            GenerationStreamState state,
            List<ChatChunk> batch
    ) {
        return repositoryCall(() -> {
            String delta = state.accept(batch);
            if (!delta.isEmpty()) {
                state.observeFirstTokenAt(clock.instant());
            }
            requireSafe(safetyPolicy.evaluatePartial(state.accumulatedContent(), evidence));
            if (evidence.isEmpty()) {
                persistDelta(generationId, leaseToken, delta);
            }
            return true;
        }).then();
    }

    Mono<Void> finish(
            UUID generationId,
            UUID leaseToken,
            ToolEvidenceSnapshot evidence,
            GenerationStreamState state
    ) {
        return repositoryCall(() -> {
            String completedContent = state.completedContent();
            requireSafe(safetyPolicy.evaluateFinal(completedContent, evidence));
            if (!evidence.isEmpty()) {
                persistDelta(generationId, leaseToken, completedContent);
            }
            return true;
        }).then(terminalCoordinator.complete(generationId, leaseToken, state));
    }

    private void persistDelta(UUID generationId, UUID leaseToken, String delta) {
        int start = 0;
        while (start < delta.length()) {
            int end = Math.min(start + MAX_PERSISTED_DELTA_CHARACTERS, delta.length());
            if (end < delta.length()
                    && Character.isHighSurrogate(delta.charAt(end - 1))
                    && Character.isLowSurrogate(delta.charAt(end))) {
                end--;
            }
            Instant now = clock.instant();
            DeltaAppendResult result = repository.appendDelta(
                    generationId,
                    leaseToken,
                    delta.substring(start, end),
                    now,
                    now.plus(properties.getLeaseDuration()),
                    now.plus(retentionPolicy.generationEventRetention())
            );
            requireActive(result);
            start = end;
        }
    }

    private static void requireSafe(OutputSafetyAssessment assessment) {
        if (assessment == null) {
            throw GenerationProcessingException.failed("AI_OUTPUT_POLICY_UNAVAILABLE");
        }
        if (!assessment.permitted()) {
            throw GenerationProcessingException.failed(assessment.reasonCode());
        }
    }

    private static void requireActive(DeltaAppendResult result) {
        switch (result) {
            case APPENDED -> {
            }
            case CANCEL_REQUESTED -> throw GenerationProcessingException.cancellationRequested();
            case STALE -> throw GenerationProcessingException.leaseLost();
        }
    }

    private <T> Mono<T> repositoryCall(Callable<T> action) {
        return Mono.fromCallable(action).subscribeOn(scheduler);
    }
}
