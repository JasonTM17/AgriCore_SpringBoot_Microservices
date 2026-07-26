package com.agricore.notification.api.advice;

import com.agricore.common.api.ApiError;
import com.agricore.common.persistence.ConstraintViolations;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Renders every error this service can produce as the platform {@link ApiError}.
 *
 * <p>The send endpoint validates the recipient and subject, and is restricted to administrators.
 * Both rejections previously rendered through Boot's default error handling, so neither a
 * validation failure nor a denial told the caller anything a client could branch on.
 *
 * <p>Only the HTTP surface is covered. {@code UserRegisteredKafkaListener} does not dispatch
 * through the servlet, so its {@code IllegalStateException} still reaches the container error
 * handler and the DLT routing is unaffected.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatusCode statusCode = ex.getStatusCode();
        HttpStatus resolved = HttpStatus.resolve(statusCode.value());
        return ResponseEntity.status(statusCode).body(ApiError.of(
                statusCode.value(),
                resolved != null ? resolved.getReasonPhrase() : "Error",
                resolved != null ? resolved.name() : "HTTP_" + statusCode.value(),
                ex.getReason(),
                request.getRequestURI(),
                null
        ));
    }

    /**
     * The consumer's {@code processed_events} composite key is the unique constraint that can fire
     * here: a redelivery racing its own first delivery inserts the same (event id, consumer) pair
     * twice. That race is benign — the work is already done — so it must not surface as a server
     * fault to whatever retried.
     *
     * <p>Only unique violations become 409. A foreign-key or not-null failure reaching this point
     * is a defect in this service and stays a 500 via the catch-all below.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        if (!ConstraintViolations.isUniqueViolation(ex)) {
            return handleGeneric(ex, request);
        }
        // The constraint name identifies the index and therefore the schema. Operators need it,
        // callers do not: the response says only that the value is taken.
        log.warn("Duplicate key on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                409, "Conflict", "DUPLICATE_RESOURCE",
                "A record with the supplied identifier already exists",
                request.getRequestURI(), null
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        var violations = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toViolation)
                .toList();
        return ResponseEntity.badRequest().body(
                ApiError.validation("Request validation failed", request.getRequestURI(), null, violations)
        );
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiError> handleAuthorizationDenied(
            AuthorizationDeniedException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of(
                403, "Forbidden", "ACCESS_DENIED", "Insufficient privileges for this operation",
                request.getRequestURI(), null
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {}", request.getRequestURI(), ex);
        return ResponseEntity.internalServerError().body(ApiError.of(
                500, "Internal Server Error", "INTERNAL_ERROR", "An unexpected error occurred",
                request.getRequestURI(), null
        ));
    }

    private ApiError.FieldViolation toViolation(FieldError error) {
        return new ApiError.FieldViolation(error.getField(), error.getDefaultMessage(), error.getRejectedValue());
    }
}
