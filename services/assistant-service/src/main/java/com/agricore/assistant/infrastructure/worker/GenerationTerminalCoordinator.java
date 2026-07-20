package com.agricore.assistant.infrastructure.worker;

import com.agricore.assistant.application.model.GenerationLeaseStatus;
import com.agricore.assistant.application.port.AssistantProviderException;
import com.agricore.assistant.application.port.AssistantRetentionPolicy;
import com.agricore.assistant.application.port.GenerationExecutionRepository;
import com.agricore.assistant.infrastructure.configuration.AssistantGenerationWorkerProperties;
import org.springframework.stereotype.Component;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

@Component
public class GenerationTerminalCoordinator {

    private final GenerationExecutionRepository repository;
    private final AssistantRetentionPolicy retentionPolicy;
    private final AssistantGenerationWorkerProperties properties;
    private final Clock clock;
    private final Scheduler scheduler;

    public GenerationTerminalCoordinator(
            GenerationExecutionRepository repository,
            AssistantRetentionPolicy retentionPolicy,
            AssistantGenerationWorkerProperties properties,
            Clock clock,
            Scheduler assistantGenerationScheduler
    ) {
        this.repository = repository;
        this.retentionPolicy = retentionPolicy;
        this.properties = properties;
        this.clock = clock;
        this.scheduler = assistantGenerationScheduler;
    }

    public Mono<Void> complete(
            UUID generationId,
            UUID leaseToken,
            GenerationStreamState state
    ) {
        return Mono.defer(() -> {
            Instant now = clock.instant();
            var completion = state.completion(
                    now, now.plus(retentionPolicy.generationEventRetention()));
            return repositoryCall(() -> repository.complete(generationId, leaseToken, completion))
                    .flatMap(result -> result.isPresent()
                            ? Mono.empty()
                            : resolveRejectedTransition(generationId, leaseToken));
        });
    }

    public Mono<Void> settle(UUID generationId, UUID leaseToken, Throwable error) {
        Throwable failure = Exceptions.unwrap(error);
        if (failure instanceof GenerationProcessingException processing) {
            return switch (processing.resolution()) {
                case CANCEL -> finishCancellation(generationId, leaseToken);
                case IGNORE -> Mono.empty();
                case FAIL -> fail(generationId, leaseToken, processing.errorCode());
            };
        }
        if (failure instanceof AssistantProviderException providerFailure) {
            return fail(generationId, leaseToken, providerFailure.getCode());
        }
        if (failure instanceof TimeoutException) {
            return fail(generationId, leaseToken, "AI_PROVIDER_TIMEOUT");
        }
        return fail(generationId, leaseToken, "GENERATION_FAILED");
    }

    private Mono<Void> fail(UUID generationId, UUID leaseToken, String errorCode) {
        Instant now = clock.instant();
        return repositoryCall(() -> repository.fail(
                generationId,
                leaseToken,
                errorCode,
                now,
                now.plus(retentionPolicy.generationEventRetention())
        )).flatMap(result -> result.isPresent()
                ? Mono.empty()
                : resolveRejectedTransition(generationId, leaseToken));
    }

    private Mono<Void> resolveRejectedTransition(UUID generationId, UUID leaseToken) {
        Instant now = clock.instant();
        return repositoryCall(() -> repository.renewLease(
                generationId,
                leaseToken,
                now,
                now.plus(properties.getLeaseDuration())
        )).flatMap(status -> switch (status) {
            case CANCEL_REQUESTED -> finishCancellation(generationId, leaseToken);
            case STALE -> Mono.empty();
            case ACTIVE -> Mono.error(new IllegalStateException("generation terminal transition was rejected"));
        });
    }

    private Mono<Void> finishCancellation(UUID generationId, UUID leaseToken) {
        Instant now = clock.instant();
        return repositoryCall(() -> repository.finishCancellation(
                generationId,
                leaseToken,
                now,
                now.plus(retentionPolicy.generationEventRetention())
        )).then();
    }

    private <T> Mono<T> repositoryCall(Callable<T> action) {
        return Mono.fromCallable(action).subscribeOn(scheduler);
    }
}
