package com.agricore.assistant.domain.model;

public enum GenerationStatus {
    QUEUED,
    RUNNING,
    CANCEL_REQUESTED,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean active() {
        return this == QUEUED || this == RUNNING || this == CANCEL_REQUESTED;
    }

    public boolean terminal() {
        return !active();
    }
}
