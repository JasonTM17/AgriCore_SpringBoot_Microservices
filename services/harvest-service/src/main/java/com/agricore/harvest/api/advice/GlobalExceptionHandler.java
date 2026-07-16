package com.agricore.harvest.api.advice;

import com.agricore.common.api.ApiError;
import com.agricore.harvest.domain.exception.HarvestException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HarvestException.class)
    public ResponseEntity<ApiError> handle(HarvestException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getHttpStatus()).body(ApiError.of(
                ex.getHttpStatus(), HttpStatus.valueOf(ex.getHttpStatus()).getReasonPhrase(),
                ex.getCode(), ex.getMessage(), request.getRequestURI(), null
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
