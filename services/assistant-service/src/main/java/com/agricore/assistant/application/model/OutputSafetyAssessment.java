package com.agricore.assistant.application.model;

public record OutputSafetyAssessment(boolean permitted, String reasonCode) {

    private static final String REASON_PATTERN = "[A-Z][A-Z0-9_]{0,63}";

    public OutputSafetyAssessment {
        reasonCode = reasonCode == null || reasonCode.isBlank() ? null : reasonCode.strip();
        if (permitted && reasonCode != null) {
            throw new IllegalArgumentException("permitted output cannot have a refusal reason");
        }
        if (!permitted && (reasonCode == null || !reasonCode.matches(REASON_PATTERN))) {
            throw new IllegalArgumentException("refused output requires a safe reason code");
        }
    }

    public static OutputSafetyAssessment allow() {
        return new OutputSafetyAssessment(true, null);
    }

    public static OutputSafetyAssessment deny(String reasonCode) {
        return new OutputSafetyAssessment(false, reasonCode);
    }
}
