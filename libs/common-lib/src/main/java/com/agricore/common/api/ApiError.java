package com.agricore.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Uniform API error body for all AgriCore services.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String traceId,
        List<FieldViolation> violations,
        Map<String, Object> details
) {
    public record FieldViolation(String field, String message, Object rejectedValue) {
    }

    public static ApiError of(int status, String error, String code, String message, String path, String traceId) {
        return new ApiError(Instant.now(), status, error, code, message, path, traceId, null, null);
    }

    public static ApiError validation(
            String message,
            String path,
            String traceId,
            List<FieldViolation> violations
    ) {
        return new ApiError(
                Instant.now(),
                400,
                "Bad Request",
                "VALIDATION_FAILED",
                message,
                path,
                traceId,
                violations,
                null
        );
    }
}
