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
}
