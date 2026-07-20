package com.agricore.assistant.infrastructure.worker;

final class GenerationProcessingException extends RuntimeException {

    enum Resolution {
        FAIL,
        CANCEL,
        IGNORE
    }

    private final String errorCode;
    private final Resolution resolution;

    private GenerationProcessingException(String errorCode, Resolution resolution) {
        super(errorCode);
        this.errorCode = errorCode;
        this.resolution = resolution;
    }

    String errorCode() {
        return errorCode;
    }

    Resolution resolution() {
        return resolution;
    }

    static GenerationProcessingException failed(String errorCode) {
        return new GenerationProcessingException(errorCode, Resolution.FAIL);
    }

    static GenerationProcessingException cancellationRequested() {
        return new GenerationProcessingException("GENERATION_CANCELLED", Resolution.CANCEL);
    }

    static GenerationProcessingException leaseLost() {
        return new GenerationProcessingException("GENERATION_LEASE_LOST", Resolution.IGNORE);
    }
}
