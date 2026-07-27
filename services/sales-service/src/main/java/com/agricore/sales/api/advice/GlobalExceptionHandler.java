package com.agricore.sales.api.advice;

import com.agricore.common.api.ApiError;
import com.agricore.common.persistence.ConstraintViolations;
import com.agricore.farmaccess.FarmAccessException;
import com.agricore.sales.domain.exception.SalesException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SalesException.class)
    public ResponseEntity<ApiError> handle(SalesException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getHttpStatus()).body(ApiError.of(
                ex.getHttpStatus(), HttpStatus.valueOf(ex.getHttpStatus()).getReasonPhrase(),
                ex.getCode(), ex.getMessage(), request.getRequestURI(), null
        ));
    }

    @ExceptionHandler(FarmAccessException.class)
    public ResponseEntity<ApiError> handleFarmAccess(FarmAccessException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getHttpStatus()).body(ApiError.of(
                ex.getHttpStatus(), HttpStatus.valueOf(ex.getHttpStatus()).getReasonPhrase(),
                ex.getCode(), ex.getMessage(), request.getRequestURI(), null
        ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> dataIntegrity(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        if (!ConstraintViolations.isUniqueViolation(ex)) {
            return error(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "DATA_INTEGRITY_ERROR",
                    "The request could not be persisted",
                    request
            );
        }
        return error(
                HttpStatus.CONFLICT,
                "DUPLICATE_RESOURCE",
                "A record with the supplied identifier already exists",
                request
        );
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

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(ApiError.of(
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                null
        ));
    }
}
