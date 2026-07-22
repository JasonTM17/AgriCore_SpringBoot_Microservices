package com.agricore.cropcatalog.api.advice;

import com.agricore.common.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiError> methodValidation(Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(
                ApiError.validation("Request validation failed", request.getRequestURI(), null, null)
        );
    }
}
