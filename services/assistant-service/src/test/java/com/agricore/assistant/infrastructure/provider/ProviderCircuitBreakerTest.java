package com.agricore.assistant.infrastructure.provider;

import com.agricore.assistant.application.model.ChatChunk;
import com.agricore.assistant.application.model.ChatGenerationRequest;
import com.agricore.assistant.application.model.ChatTurn;
import com.agricore.assistant.application.model.ChatTurnRole;
import com.agricore.assistant.application.port.AssistantProviderException;
import com.agricore.assistant.application.port.ChatProvider;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderCircuitBreakerTest {

    private final ChatGenerationRequest request = new ChatGenerationRequest(
            List.of(new ChatTurn(ChatTurnRole.USER, "hello")),
            "test-model",
            32,
            0
    );

    @Test
    void opensAfterRetryableFailuresAndAllowsOneHalfOpenProbe() throws Exception {
        ProviderCircuitBreaker breaker = new ProviderCircuitBreaker(2, Duration.ofMillis(30));

        assertThat(breaker.tryAcquire()).isTrue();
        breaker.onFailure(AssistantProviderException.failed());
        assertThat(breaker.tryAcquire()).isTrue();
        breaker.onFailure(AssistantProviderException.timedOut(null));
        assertThat(breaker.tryAcquire()).isFalse();

        Thread.sleep(45);
        assertThat(breaker.tryAcquire()).isTrue();
        assertThat(breaker.tryAcquire()).isFalse();
        breaker.onSuccess();
        assertThat(breaker.tryAcquire()).isTrue();
    }

    @Test
    void ignoresNonRetryableFailuresForCircuitState() {
        ProviderCircuitBreaker breaker = new ProviderCircuitBreaker(1, Duration.ofSeconds(1));

        assertThat(breaker.tryAcquire()).isTrue();
        breaker.onFailure(AssistantProviderException.authenticationFailed(null));

        assertThat(breaker.tryAcquire()).isTrue();
        assertThat(breaker.isOpen()).isFalse();
    }

    @Test
    void decoratorCountsOnlyTerminalSuccessAndMapsOpenState() {
        AtomicInteger calls = new AtomicInteger();
        ChatProvider delegate = new ChatProvider() {
            @Override
            public com.agricore.assistant.application.model.ProviderCapabilities capabilities() {
                return new com.agricore.assistant.application.model.ProviderCapabilities(
                        "test", true, true, null
                );
            }

            @Override
            public Flux<ChatChunk> stream(ChatGenerationRequest ignored) {
                calls.incrementAndGet();
                return Flux.error(AssistantProviderException.failed());
            }
        };
        CircuitBreakingChatProvider provider = new CircuitBreakingChatProvider(
                delegate,
                new ProviderCircuitBreaker(1, Duration.ofSeconds(1))
        );

        assertThatThrownBy(() -> provider.stream(request).blockLast())
                .isInstanceOf(AssistantProviderException.class);
        assertThatThrownBy(() -> provider.stream(request).blockLast())
                .isInstanceOfSatisfying(AssistantProviderException.class, error ->
                        assertThat(error.getCode()).isEqualTo("AI_PROVIDER_CIRCUIT_OPEN"));
        assertThat(calls).hasValue(1);
        assertThat(provider.capabilities().reasonCode()).isEqualTo("AI_PROVIDER_CIRCUIT_OPEN");
    }

    @Test
    void terminalSuccessResetsFailures() {
        AtomicInteger calls = new AtomicInteger();
        ChatProvider delegate = new ChatProvider() {
            @Override
            public com.agricore.assistant.application.model.ProviderCapabilities capabilities() {
                return new com.agricore.assistant.application.model.ProviderCapabilities(
                        "test", true, true, null
                );
            }

            @Override
            public Flux<ChatChunk> stream(ChatGenerationRequest ignored) {
                calls.incrementAndGet();
                return Flux.just(ChatChunk.terminal("stop", 1, 1));
            }
        };
        CircuitBreakingChatProvider provider = new CircuitBreakingChatProvider(
                delegate,
                new ProviderCircuitBreaker(1, Duration.ofSeconds(1))
        );

        provider.stream(request).blockLast();
        provider.stream(request).blockLast();

        assertThat(calls).hasValue(2);
        assertThat(provider.capabilities().available()).isTrue();
    }
}
