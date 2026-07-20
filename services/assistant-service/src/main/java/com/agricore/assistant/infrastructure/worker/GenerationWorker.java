package com.agricore.assistant.infrastructure.worker;

import com.agricore.assistant.application.model.ChatChunk;
import com.agricore.assistant.application.model.ChatGenerationRequest;
import com.agricore.assistant.application.model.DeltaAppendResult;
import com.agricore.assistant.application.model.GenerationExecutionContext;
import com.agricore.assistant.application.model.GenerationLeaseStatus;
import com.agricore.assistant.application.port.AssistantRetentionPolicy;
import com.agricore.assistant.application.port.ChatGenerationPolicy;
import com.agricore.assistant.application.port.ChatProvider;
import com.agricore.assistant.application.port.GenerationExecutionRepository;
import com.agricore.assistant.infrastructure.configuration.AssistantGenerationWorkerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;

@Component
public class GenerationWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerationWorker.class);
    private static final int MAX_PERSISTED_DELTA_CHARACTERS = 8_000;

    private final GenerationExecutionRepository repository;
    private final ChatProvider chatProvider;
    private final ChatGenerationPolicy generationPolicy;
    private final AssistantRetentionPolicy retentionPolicy;
    private final AssistantGenerationWorkerProperties properties;
    private final GenerationProviderGuard providerGuard;
    private final GenerationChatRequestFactory requestFactory;
    private final GenerationTerminalCoordinator terminalCoordinator;
    private final Clock clock;
    private final Scheduler scheduler;

    public GenerationWorker(
            GenerationExecutionRepository repository,
            ChatProvider chatProvider,
            ChatGenerationPolicy generationPolicy,
            AssistantRetentionPolicy retentionPolicy,
            AssistantGenerationWorkerProperties properties,
            GenerationProviderGuard providerGuard,
            GenerationChatRequestFactory requestFactory,
            GenerationTerminalCoordinator terminalCoordinator,
            Clock clock,
            Scheduler assistantGenerationScheduler
    ) {
        this.repository = repository;
        this.chatProvider = chatProvider;
        this.generationPolicy = generationPolicy;
        this.retentionPolicy = retentionPolicy;
        this.properties = properties;
        this.providerGuard = providerGuard;
        this.requestFactory = requestFactory;
        this.terminalCoordinator = terminalCoordinator;
        this.clock = clock;
        this.scheduler = assistantGenerationScheduler;
    }

    public Mono<Void> execute(UUID generationId) {
        Objects.requireNonNull(generationId, "generationId is required");
        UUID leaseToken = UUID.randomUUID();
        Instant now = clock.instant();
        return repositoryCall(() -> repository.claim(
                generationId,
                leaseToken,
                now,
                now.plus(properties.getLeaseDuration()),
                now.plus(retentionPolicy.generationEventRetention())
        )).flatMap(context -> context
                .map(value -> executeClaimed(generationId, leaseToken, value)
                        .onErrorResume(error -> terminalCoordinator.settle(
                                generationId, leaseToken, error)))
                .orElseGet(Mono::empty)
        ).doOnError(error -> LOGGER.warn(
                "Generation worker stopped unexpectedly generationId={} errorType={}",
                generationId,
                error.getClass().getSimpleName()
        )).onErrorResume(error -> Mono.empty());
    }

    private Mono<Void> executeClaimed(
            UUID generationId,
            UUID leaseToken,
            GenerationExecutionContext context
    ) {
        return Mono.defer(() -> {
            providerGuard.verify(context);
            ChatGenerationRequest request = requestFactory.create(context);
            GenerationStreamState state = new GenerationStreamState();
            Mono<Void> providerStream = providerStream(generationId, leaseToken, request, state);
            Mono<Void> heartbeat = heartbeat(generationId, leaseToken);
            return Mono.firstWithSignal(providerStream, heartbeat);
        });
    }

    private Mono<Void> providerStream(
            UUID generationId,
            UUID leaseToken,
            ChatGenerationRequest request,
            GenerationStreamState state
    ) {
        return Flux.defer(() -> chatProvider.stream(request))
                .timeout(generationPolicy.maxGenerationDuration())
                .bufferTimeout(properties.getDeltaBatchSize(), properties.getDeltaFlushInterval())
                .takeUntil(this::containsTerminal)
                .concatMap(batch -> persistBatch(generationId, leaseToken, state, batch))
                .then(terminalCoordinator.complete(generationId, leaseToken, state));
    }

    private Mono<Void> persistBatch(
            UUID generationId,
            UUID leaseToken,
            GenerationStreamState state,
            List<ChatChunk> batch
    ) {
        return repositoryCall(() -> {
            String delta = state.accept(batch);
            persistDelta(generationId, leaseToken, delta);
            return true;
        }).then();
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

    private Mono<Void> heartbeat(UUID generationId, UUID leaseToken) {
        return Flux.interval(properties.getHeartbeatInterval())
                .concatMap(ignored -> repositoryCall(() -> {
                    Instant now = clock.instant();
                    GenerationLeaseStatus status = repository.renewLease(
                            generationId,
                            leaseToken,
                            now,
                            now.plus(properties.getLeaseDuration())
                    );
                    requireActive(status);
                    return status;
                }))
                .then();
    }

    private boolean containsTerminal(List<ChatChunk> batch) {
        return batch.stream().anyMatch(ChatChunk::terminal);
    }

    private void requireActive(DeltaAppendResult result) {
        switch (result) {
            case APPENDED -> {
            }
            case CANCEL_REQUESTED -> throw GenerationProcessingException.cancellationRequested();
            case STALE -> throw GenerationProcessingException.leaseLost();
        }
    }

    private void requireActive(GenerationLeaseStatus status) {
        switch (status) {
            case ACTIVE -> {
            }
            case CANCEL_REQUESTED -> throw GenerationProcessingException.cancellationRequested();
            case STALE -> throw GenerationProcessingException.leaseLost();
        }
    }

    private <T> Mono<T> repositoryCall(Callable<T> action) {
        return Mono.fromCallable(action).subscribeOn(scheduler);
    }
}
