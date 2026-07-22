package com.agricore.assistant.domain.exception;

public class AssistantException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public AssistantException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public static AssistantException notFound() {
        return new AssistantException(
                "CONVERSATION_NOT_FOUND", "Conversation not found", 404
        );
    }

    public static AssistantException invalidContext() {
        return new AssistantException(
                "INVALID_CONVERSATION_CONTEXT",
                "Farm context and farmId must match",
                400
        );
    }

    public static AssistantException invalidTitle() {
        return new AssistantException(
                "INVALID_CONVERSATION_TITLE", "Conversation title is invalid", 400
        );
    }

    public static AssistantException invalidActorSubject() {
        return new AssistantException(
                "INVALID_ACTOR_SUBJECT", "Authenticated subject is invalid", 401
        );
    }

    public static AssistantException conversationNotOpen() {
        return new AssistantException(
                "CONVERSATION_NOT_OPEN", "Conversation is not open for a new generation", 409
        );
    }

    public static AssistantException generationAlreadyActive() {
        return new AssistantException(
                "GENERATION_ALREADY_ACTIVE", "Conversation already has an active generation", 409
        );
    }

    public static AssistantException idempotencyKeyReused() {
        return new AssistantException(
                "IDEMPOTENCY_KEY_REUSED", "Idempotency key was used with a different request", 409
        );
    }

    public static AssistantException generationNotFound() {
        return new AssistantException(
                "GENERATION_NOT_FOUND", "Generation not found", 404
        );
    }

    public static AssistantException invalidEventCursor() {
        return new AssistantException(
                "INVALID_EVENT_CURSOR", "Generation event cursor is invalid", 400
        );
    }

    public static AssistantException eventReplayExpired() {
        return new AssistantException(
                "GENERATION_EVENT_REPLAY_EXPIRED", "Generation event replay window expired", 410
        );
    }

    public static AssistantException streamCapacityExceeded() {
        return new AssistantException(
                "GENERATION_STREAM_CAPACITY_EXCEEDED", "Generation stream capacity is temporarily exhausted", 503
        );
    }

    public static AssistantException providerUnavailable(String reasonCode) {
        String safeCode = switch (reasonCode) {
            case "AI_PROVIDER_CONFIGURATION_MISSING", "AI_PROVIDER_ADAPTER_UNAVAILABLE",
                 "AI_PROVIDER_CIRCUIT_OPEN" -> reasonCode;
            default -> "AI_PROVIDER_UNAVAILABLE";
        };
        return new AssistantException(
                safeCode, "The configured AI provider is unavailable", 503
        );
    }

    public static AssistantException toolContextUnavailable(String reasonCode) {
        if ("TOOL_SCOPE_UNAVAILABLE".equals(reasonCode)) {
            return new AssistantException(
                    reasonCode, "Conversation context is not available to the caller", 404
            );
        }
        return new AssistantException(
                "TOOL_AUTHORIZATION_UNAVAILABLE",
                "Authorized tool context is temporarily unavailable",
                503
        );
    }
}
