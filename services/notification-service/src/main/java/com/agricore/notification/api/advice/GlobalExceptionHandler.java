package com.agricore.notification.api.advice;

import com.agricore.common.api.ApiError;
import com.agricore.common.persistence.ConstraintViolations;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> responseStatus(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        HttpStatusCode statusCode = exception.getStatusCode();
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String reason = status != null ? status.getReasonPhrase() : "Error";
        return ResponseEntity.status(statusCode).body(ApiError.of(
                statusCode.value(),
                reason,
                status != null ? status.name() : "HTTP_" + statusCode.value(),
                exception.getReason() != null ? exception.getReason() : reason,
                request.getRequestURI(),
                null
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadableBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        log.debug("Unreadable request body on {}", request.getRequestURI(), exception);
        return ResponseEntity.badRequest().body(ApiError.of(
                400,
                "Bad Request",
                "MALFORMED_REQUEST",
                "Request body is missing or is not valid JSON",
                request.getRequestURI(),
                null
        ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> dataIntegrity(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        if (!ConstraintViolations.isUniqueViolation(exception)) {
            return generic(exception, request);
        }
        log.warn("Duplicate key on {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                409,
                "Conflict",
                "DUPLICATE_RESOURCE",
                "A record with the supplied identifier already exists",
                request.getRequestURI(),
                null
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> bodyValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        var violations = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toViolation)
                .toList();
        return ResponseEntity.badRequest().body(
                ApiError.validation("Request validation failed", request.getRequestURI(), null, violations)
        );
    }

    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiError> methodValidation(Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(
                ApiError.validation("Request validation failed", request.getRequestURI(), null, null)
        );
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiError> authorizationDenied(
            AuthorizationDeniedException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of(
                403,
                "Forbidden",
                "ACCESS_DENIED",
                "Insufficient privileges for this operation",
                request.getRequestURI(),
                null
        ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.badRequest().body(ApiError.of(
                400,
                "Bad Request",
                "INVALID_PARAMETER",
                "Parameter '" + exception.getName() + "' has an invalid value",
                request.getRequestURI(),
                null
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> generic(Exception exception, HttpServletRequest request) {
        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatusCode statusCode = errorResponse.getStatusCode();
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            String reason = status != null ? status.getReasonPhrase() : "Error";
            return ResponseEntity.status(statusCode).body(ApiError.of(
                    statusCode.value(),
                    reason,
                    status != null ? status.name() : "HTTP_" + statusCode.value(),
                    reason,
                    request.getRequestURI(),
                    null
            ));
        }
        log.error("Unhandled error on {}", request.getRequestURI(), exception);
        return ResponseEntity.internalServerError().body(ApiError.of(
                500,
                "Internal Server Error",
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request.getRequestURI(),
                null
        ));
    }

    private ApiError.FieldViolation toViolation(FieldError error) {
        return new ApiError.FieldViolation(error.getField(), error.getDefaultMessage(), error.getRejectedValue());
    }
}
