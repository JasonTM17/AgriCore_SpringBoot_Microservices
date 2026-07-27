package com.agricore.cropcatalog.api.advice;

import com.agricore.common.api.ApiError;
import com.agricore.common.persistence.ConstraintViolations;
import com.agricore.cropcatalog.domain.exception.CatalogException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CatalogException.class)
    public ResponseEntity<ApiError> catalog(CatalogException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getHttpStatus());
        return ResponseEntity.status(status).body(ApiError.of(
                status.value(),
                status.getReasonPhrase(),
                exception.getCode(),
                exception.getMessage(),
                request.getRequestURI(),
                null
        ));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> optimistic(
            ObjectOptimisticLockingFailureException exception,
            HttpServletRequest request
    ) {
        return error(
                HttpStatus.CONFLICT,
                "OPTIMISTIC_LOCK",
                "Crop care profile changed concurrently; reload the latest state before retrying",
                request
        );
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiError> forbidden(
            AuthorizationDeniedException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Insufficient privileges for this operation", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> bodyValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        var violations = exception.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toViolation)
                .toList();
        return ResponseEntity.badRequest().body(
                ApiError.validation("Request validation failed", request.getRequestURI(), null, violations)
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> malformedJson(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "Malformed JSON request", request);
    }

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

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> dataIntegrity(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        if (!ConstraintViolations.isUniqueViolation(exception)) {
            return generic(exception, request);
        }
        log.warn("Duplicate key on {}", request.getRequestURI());
        return error(
                HttpStatus.CONFLICT,
                "DUPLICATE_RESOURCE",
                "A record with the supplied identifier already exists",
                request
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> unsupportedMediaType(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Content-Type is not supported", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER",
                "Parameter '" + exception.getName() + "' has an invalid value",
                request
        );
    }

    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiError> methodValidation(Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(
                ApiError.validation("Request validation failed", request.getRequestURI(), null, null)
        );
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
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request
        );
    }

    private static ApiError.FieldViolation toViolation(FieldError error) {
        return new ApiError.FieldViolation(error.getField(), error.getDefaultMessage(), error.getRejectedValue());
    }

    private static ResponseEntity<ApiError> error(
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
