package com.agricore.assistant.infrastructure.provider;

import com.agricore.assistant.application.port.AssistantProviderException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.SocketTimeoutException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

final class ProviderFailureMapper {

    private ProviderFailureMapper() {
    }

    static Throwable map(Throwable failure) {
        if (failure instanceof AssistantProviderException
                || failure instanceof CancellationException
                || failure instanceof Error) {
            return failure;
        }
        Integer status = findHttpStatus(failure);
        if (status != null) {
            if (status == 408) {
                return AssistantProviderException.timedOut(failure);
            }
            if (status == 429) {
                return AssistantProviderException.rateLimited(failure);
            }
            if (status == 401 || status == 403) {
                return AssistantProviderException.authenticationFailed(failure);
            }
            if (status >= 400 && status < 500) {
                return AssistantProviderException.requestRejected(failure);
            }
        }
        if (hasTimeoutCause(failure)) {
            return AssistantProviderException.timedOut(failure);
        }
        return AssistantProviderException.failed(failure);
    }

    private static Integer findHttpStatus(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (current instanceof WebClientResponseException webClientFailure) {
                return webClientFailure.getStatusCode().value();
            }
            if (current instanceof RestClientResponseException restClientFailure) {
                return restClientFailure.getStatusCode().value();
            }
            current = current.getCause();
        }
        return null;
    }

    private static boolean hasTimeoutCause(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (current instanceof TimeoutException
                    || current instanceof SocketTimeoutException
                    || current.getClass().getSimpleName().endsWith("TimeoutException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
