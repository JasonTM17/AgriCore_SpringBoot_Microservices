package com.agricore.assistant.api.advice;

import com.agricore.assistant.domain.AssistantException;
import com.agricore.common.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AssistantException.class)
    public ResponseEntity<ApiError> handleAssistant(AssistantException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getHttpStatus()).body(
                ApiError.of(
                        ex.getHttpStatus(),
                        HttpStatus.valueOf(ex.getHttpStatus()).getReasonPhrase(),
                        ex.getCode(),
                        ex.getMessage(),
                        request.getRequestURI(),
                        null
                )
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(
                ApiError.of(400, "Bad Request", "VALIDATION_FAILED", "Request validation failed",
                        request.getRequestURI(), null)
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled assistant error on {}", request.getRequestURI(), ex);
        return ResponseEntity.internalServerError().body(
                ApiError.of(500, "Internal Server Error", "INTERNAL_ERROR", "An unexpected error occurred",
                        request.getRequestURI(), null)
        );
    }
}
