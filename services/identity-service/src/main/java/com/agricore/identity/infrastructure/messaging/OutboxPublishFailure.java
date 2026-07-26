package com.agricore.identity.infrastructure.messaging;

import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.SerializationException;

final class OutboxPublishFailure {

    private static final int MAX_CAUSE_DEPTH = 16;
    private static final int MAX_DIAGNOSTIC_LENGTH = 1_000;

    private final String diagnostic;
    private final boolean permanent;

    private OutboxPublishFailure(String diagnostic, boolean permanent) {
        this.diagnostic = sanitize(diagnostic);
        this.permanent = permanent;
    }

    static OutboxPublishFailure transientFailure(String diagnostic) {
        return new OutboxPublishFailure(diagnostic, false);
    }

    static OutboxPublishFailure from(Exception exception) {
        Throwable cause = exception;
        Throwable diagnosticCause = exception;
        boolean permanent = false;
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++) {
            diagnosticCause = cause;
            if (cause instanceof SerializationException
                    || cause instanceof RecordTooLargeException
                    || cause instanceof InvalidTopicException) {
                permanent = true;
            }
            Throwable next = cause.getCause();
            if (next == null || next == cause) {
                break;
            }
            cause = next;
        }
        String message = diagnosticCause.getMessage();
        String diagnostic = message == null || message.isBlank()
                ? diagnosticCause.getClass().getSimpleName()
                : message;
        return new OutboxPublishFailure(diagnostic, permanent);
    }

    String diagnostic() {
        return diagnostic;
    }

    boolean permanent() {
        return permanent;
    }

    private static String sanitize(String diagnostic) {
        String normalized = diagnostic == null || diagnostic.isBlank() ? "unknown" : diagnostic;
        String bounded = normalized.substring(0, Math.min(normalized.length(), MAX_DIAGNOSTIC_LENGTH));
        return bounded.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
    }
}
