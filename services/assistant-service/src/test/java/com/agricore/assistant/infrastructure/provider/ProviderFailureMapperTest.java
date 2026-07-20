package com.agricore.assistant.infrastructure.provider;

import com.agricore.assistant.application.port.AssistantProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderFailureMapperTest {

    @Test
    void mapsHttpAndTimeoutFailuresToSafeRetrySemantics() {
        assertThat(ProviderFailureMapper.map(response(429)))
                .isInstanceOfSatisfying(AssistantProviderException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("AI_PROVIDER_RATE_LIMITED");
                    assertThat(error.isRetryable()).isTrue();
                });
        assertThat(ProviderFailureMapper.map(response(401)))
                .isInstanceOfSatisfying(AssistantProviderException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("AI_PROVIDER_AUTHENTICATION_FAILED");
                    assertThat(error.isRetryable()).isFalse();
                });
        assertThat(ProviderFailureMapper.map(response(422)))
                .isInstanceOfSatisfying(AssistantProviderException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("AI_PROVIDER_REQUEST_REJECTED");
                    assertThat(error.isRetryable()).isFalse();
                });
        assertThat(ProviderFailureMapper.map(response(503)))
                .isInstanceOfSatisfying(AssistantProviderException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("AI_PROVIDER_FAILED");
                    assertThat(error.isRetryable()).isTrue();
                });
        assertThat(ProviderFailureMapper.map(new TimeoutException("provider timeout")))
                .isInstanceOfSatisfying(AssistantProviderException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("AI_PROVIDER_TIMEOUT");
                    assertThat(error.isRetryable()).isTrue();
                });
    }

    @Test
    void leavesCancellationAndFatalErrorsUntouched() {
        RuntimeException cancellation = new java.util.concurrent.CancellationException();
        assertThat(ProviderFailureMapper.map(cancellation)).isSameAs(cancellation);

        AssertionError fatal = new AssertionError("fatal");
        assertThat(ProviderFailureMapper.map(fatal)).isSameAs(fatal);
    }

    private WebClientResponseException response(int status) {
        return WebClientResponseException.create(
                status,
                "provider response",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );
    }
}
