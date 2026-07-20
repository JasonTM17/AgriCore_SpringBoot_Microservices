package com.agricore.assistant.application.port;

public class AssistantProviderException extends RuntimeException {

    private final String code;
    private final boolean retryable;

    private AssistantProviderException(String code, String message, boolean retryable) {
        super(message);
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
        return new AssistantProviderException(
                "AI_PROVIDER_FAILED",
                "The AI provider failed to complete the request",
                true
        );
    }
}
