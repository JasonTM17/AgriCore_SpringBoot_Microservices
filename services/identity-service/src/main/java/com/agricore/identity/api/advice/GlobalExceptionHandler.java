package com.agricore.identity.api.advice;

import com.agricore.common.api.ApiError;
import com.agricore.common.persistence.ConstraintViolations;
import com.agricore.identity.domain.exception.IdentityException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatusCode;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IdentityException.class)
    public ResponseEntity<ApiError> handleIdentity(IdentityException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(
                ex.getHttpStatus(),
                HttpStatus.valueOf(ex.getHttpStatus()).getReasonPhrase(),
                ex.getCode(),
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }

    /**
     * Registration rejects a taken email before inserting, but that check is not a lock: two
     * simultaneous registrations for one address both pass it and {@code uk_users_email} rejects
     * the second. A 500 there invites the client to retry a request that can never succeed.
     *
     * <p>Deliberately does not distinguish which value collided. Email is the only caller-supplied
     * unique column on this service, so a response naming the constraint would confirm to an
     * unauthenticated caller that an address is registered.
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
        log.warn("Duplicate key on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                409, "Conflict", "DUPLICATE_RESOURCE",
                "A record with the supplied identifier already exists",
                request.getRequestURI(), null
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toViolation)
                .toList();
        return ResponseEntity.badRequest().body(
                ApiError.validation("Request validation failed", request.getRequestURI(), null, violations)
        );
    }

    /**
     * A path variable or query parameter that will not convert is the caller's mistake, not a
     * server fault. Without this, an admin route given a malformed user id answers 500.
     *
     * <p>The rejected value is not echoed back — only the parameter name, which this service
     * defines.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.badRequest().body(ApiError.of(
                400, "Bad Request", "INVALID_PARAMETER",
                "Parameter '" + ex.getName() + "' has an invalid value",
                request.getRequestURI(), null
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        // Spring's own web exceptions already know the status they should answer with — unknown
        // path 404, wrong method 405, unreadable body 400. Declaring a handler for Exception takes
        // precedence over DefaultHandlerExceptionResolver, so without this branch every one of them
        // would be reported as a server fault. On the auth endpoints that also means a malformed
        // login body would look like an outage rather than a bad request.
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode statusCode = errorResponse.getStatusCode();
            HttpStatus resolved = HttpStatus.resolve(statusCode.value());
            String reason = resolved != null ? resolved.getReasonPhrase() : "Error";
            return ResponseEntity.status(statusCode).body(ApiError.of(
                    statusCode.value(), reason,
                    resolved != null ? resolved.name() : "HTTP_" + statusCode.value(),
                    reason, request.getRequestURI(), null
            ));
        }
        log.error("Unhandled error on {}", request.getRequestURI(), ex);
        ApiError body = ApiError.of(
                500,
                "Internal Server Error",
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request.getRequestURI(),
                null
        );
        return ResponseEntity.internalServerError().body(body);
    }

    private ApiError.FieldViolation toViolation(FieldError error) {
        return new ApiError.FieldViolation(error.getField(), error.getDefaultMessage(), error.getRejectedValue());
    }
}
