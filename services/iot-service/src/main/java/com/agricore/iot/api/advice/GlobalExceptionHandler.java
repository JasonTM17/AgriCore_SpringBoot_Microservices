package com.agricore.iot.api.advice;

import com.agricore.common.api.ApiError;
import com.agricore.common.persistence.ConstraintViolations;
import com.agricore.iot.domain.exception.IotException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IotException.class)
    public ResponseEntity<ApiError> handle(IotException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getHttpStatus()).body(ApiError.of(
                ex.getHttpStatus(), HttpStatus.valueOf(ex.getHttpStatus()).getReasonPhrase(),
                ex.getCode(), ex.getMessage(), request.getRequestURI(), null
        ));
    }

    /**
     * A losing race on a unique index is the caller's conflict, not a server fault. Device
     * registration checks for an existing code first, but that check is not a lock: two requests
     * can both pass it and the database rejects the second.
     *
     * <p>Only unique violations become 409. A foreign-key or not-null failure reaching this point
     * is a defect in this service and stays a 500 — answering "already exists" to either would
     * replace one wrong answer with another.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> dataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        if (!ConstraintViolations.isUniqueViolation(ex)) {
            log.error("Constraint failure on {}", request.getRequestURI(), ex);
            return ResponseEntity.internalServerError().body(ApiError.of(
                    500, "Internal Server Error", "INTERNAL_ERROR", "An unexpected error occurred",
                    request.getRequestURI(), null
            ));
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
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        var violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldViolation(fe.getField(), fe.getDefaultMessage(), fe.getRejectedValue()))
                .toList();
        return ResponseEntity.badRequest().body(
                ApiError.validation("Request validation failed", request.getRequestURI(), null, violations)
        );
    }
}
