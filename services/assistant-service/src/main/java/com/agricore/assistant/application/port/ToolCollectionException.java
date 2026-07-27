package com.agricore.assistant.application.port;

public final class ToolCollectionException extends RuntimeException {

    private final String reasonCode;

    private ToolCollectionException(String reasonCode) {
        super("Authorized tool evidence is unavailable");
        this.reasonCode = reasonCode;
    }

    public static ToolCollectionException authorizationUnavailable() {
        return new ToolCollectionException("TOOL_AUTHORIZATION_UNAVAILABLE");
    }

    public static ToolCollectionException scopeUnavailable() {
        return new ToolCollectionException("TOOL_SCOPE_UNAVAILABLE");
    }

    public static ToolCollectionException rateLimited() {
        return new ToolCollectionException("TOOL_DEPENDENCY_RATE_LIMITED");
    }

    public static ToolCollectionException dependencyUnavailable() {
        return new ToolCollectionException("TOOL_DEPENDENCY_UNAVAILABLE");
    }

    public static ToolCollectionException responseInvalid() {
        return new ToolCollectionException("TOOL_RESPONSE_INVALID");
    }

    public String reasonCode() {
        return reasonCode;
    }
}
