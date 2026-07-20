package com.agricore.assistant.application.port;

public class AssistantProviderException extends RuntimeException {

    private final String code;
    private final boolean retryable;

    private AssistantProviderException(String code, String message, boolean retryable) {
        this(code, message, retryable, null);
    }

    private AssistantProviderException(String code, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
    }

    public String getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public static AssistantProviderException unavailable() {
        return new AssistantProviderException(
                "AI_PROVIDER_UNAVAILABLE",
                "The configured AI provider is unavailable",
                false
        );
    }

    public static AssistantProviderException failed() {
        return failed(null);
    }

    public static AssistantProviderException timedOut(Throwable cause) {
        return new AssistantProviderException(
                "AI_PROVIDER_TIMEOUT",
                "The AI provider request timed out",
                true,
                cause
        );
    }

    public static AssistantProviderException rateLimited(Throwable cause) {
        return new AssistantProviderException(
                "AI_PROVIDER_RATE_LIMITED",
                "The AI provider rate limit was reached",
                true,
                cause
        );
    }

    public static AssistantProviderException authenticationFailed(Throwable cause) {
        return new AssistantProviderException(
                "AI_PROVIDER_AUTHENTICATION_FAILED",
                "The AI provider rejected its configured credentials",
                false,
                cause
        );
    }

    public static AssistantProviderException requestRejected(Throwable cause) {
        return new AssistantProviderException(
                "AI_PROVIDER_REQUEST_REJECTED",
                "The AI provider rejected the request",
                false,
                cause
        );
    }

    public static AssistantProviderException failed(Throwable cause) {
        return new AssistantProviderException(
                "AI_PROVIDER_FAILED",
                "The AI provider failed to complete the request",
                true,
                cause
        );
    }
}
