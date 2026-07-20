package com.agricore.assistant.infrastructure.provider;

import com.agricore.assistant.application.model.ChatChunk;
import com.agricore.assistant.application.model.ChatGenerationRequest;
import com.agricore.assistant.application.model.ProviderCapabilities;
import com.agricore.assistant.application.port.AssistantProviderException;
import com.agricore.assistant.application.port.ChatProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CircuitBreakingChatProvider implements ChatProvider {

    private final ChatProvider delegate;
    private final ProviderCircuitBreaker circuitBreaker;

    public CircuitBreakingChatProvider(ChatProvider delegate, ProviderCircuitBreaker circuitBreaker) {
        this.delegate = Objects.requireNonNull(delegate, "delegate is required");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker is required");
    }

    @Override
    public ProviderCapabilities capabilities() {
        ProviderCapabilities capabilities = delegate.capabilities();
        if (!circuitBreaker.isOpen()) {
            return capabilities;
        }
        return new ProviderCapabilities(
                capabilities.provider(),
                false,
                capabilities.streaming(),
                "AI_PROVIDER_CIRCUIT_OPEN"
        );
    }

    @Override
    public Flux<ChatChunk> stream(ChatGenerationRequest request) {
        return Flux.defer(() -> {
            if (!circuitBreaker.tryAcquire()) {
                return Flux.error(AssistantProviderException.circuitOpen());
            }
            AtomicBoolean settled = new AtomicBoolean();
            try {
                return delegate.stream(request)
                        .doOnNext(chunk -> {
                            if (chunk.terminal()) {
                                settleSuccess(settled);
                            }
                        })
                        .doOnError(error -> settleFailure(settled, error))
                        .doFinally(signal -> {
                            if (settled.get()) {
                                return;
                            }
                            if (signal == SignalType.CANCEL) {
                                if (settled.compareAndSet(false, true)) {
                                    circuitBreaker.onCancellation();
                                }
                            } else if (settled.compareAndSet(false, true)) {
                                circuitBreaker.onFailure(AssistantProviderException.failed());
                            }
                        });
            } catch (Throwable failure) {
                if (!(failure instanceof Error) && !(failure instanceof CancellationException)) {
                    circuitBreaker.onFailure(failure);
                }
                return Flux.error(failure);
            }
        });
    }

    private void settleSuccess(AtomicBoolean settled) {
        if (settled.compareAndSet(false, true)) {
            circuitBreaker.onSuccess();
        }
    }

    private void settleFailure(AtomicBoolean settled, Throwable failure) {
        if (settled.compareAndSet(false, true)) {
            circuitBreaker.onFailure(failure);
        }
    }
}
