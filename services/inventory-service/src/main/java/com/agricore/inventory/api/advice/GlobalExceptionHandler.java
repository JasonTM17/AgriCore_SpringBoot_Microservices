package com.agricore.inventory.api.advice;

import com.agricore.common.api.ApiError;
import com.agricore.common.persistence.ConstraintViolations;
import com.agricore.farmaccess.FarmAccessException;
import com.agricore.inventory.domain.exception.InventoryException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String RESERVATION_REFERENCE_CONSTRAINT =
            "uk_inventory_reservations_reference";

    @ExceptionHandler(InventoryException.class)
    public ResponseEntity<ApiError> handle(InventoryException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getHttpStatus()).body(ApiError.of(
                ex.getHttpStatus(), HttpStatus.valueOf(ex.getHttpStatus()).getReasonPhrase(),
                ex.getCode(), ex.getMessage(), request.getRequestURI(), null
        ));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> optimistic(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return ResponseEntity.status(409).body(ApiError.of(
                409, "Conflict", "OPTIMISTIC_LOCK",
                "Concurrent stock update conflict; retry the request",
                request.getRequestURI(), null
        ));
    }

    @ExceptionHandler(FarmAccessException.class)
    public ResponseEntity<ApiError> farmAccess(FarmAccessException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getHttpStatus()).body(ApiError.of(
                ex.getHttpStatus(),
                HttpStatus.valueOf(ex.getHttpStatus()).getReasonPhrase(),
                ex.getCode(),
                ex.getMessage(),
                request.getRequestURI(),
                null
        ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> dataIntegrity(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        if (hasConstraint(ex, RESERVATION_REFERENCE_CONSTRAINT)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                    409,
                    "Conflict",
                    "RESERVATION_REFERENCE_CONFLICT",
                    "Reservation reference is already associated with a different request",
                    request.getRequestURI(),
                    null
            ));
        }
        if (ConstraintViolations.isUniqueViolation(ex)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                    409,
                    "Conflict",
                    "DUPLICATE_RESOURCE",
                    "A record with the supplied identifier already exists",
                    request.getRequestURI(),
                    null
            ));
        }
        return ResponseEntity.internalServerError().body(ApiError.of(
                500,
                "Internal Server Error",
                "DATA_INTEGRITY_ERROR",
                "The request could not be persisted",
                request.getRequestURI(),
                null
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

    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiError> methodValidation(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(
                400,
                "Bad Request",
                "VALIDATION_FAILED",
                "Request validation failed",
                request.getRequestURI(),
                null
        ));
    }

    private static boolean hasConstraint(Throwable failure, String expectedConstraint) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException violation
                    && expectedConstraint.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
