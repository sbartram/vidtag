package org.bartram.vidtag.handler;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bartram.vidtag.dto.error.DebugInfo;
import org.bartram.vidtag.dto.error.ErrorResponse;
import org.bartram.vidtag.dto.error.ValidationErrorResponse;
import org.bartram.vidtag.exception.ExternalServiceException;
import org.bartram.vidtag.exception.VidtagException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler for all REST controllers.
 * Maps exceptions to structured error responses with proper HTTP status codes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Value("${vidtag.debug-mode:false}")
    private boolean debugModeEnabled;

    @Value("${vidtag.error.max-stack-trace-length:2000}")
    private int maxStackTraceLength;

    /**
     * Handle validation errors from @Validated annotations.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        List<ValidationErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(fe -> new ValidationErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
            .toList();

        String requestId = generateRequestId();
        log.warn("Validation failed [requestId={}]: {}", requestId, fieldErrors);

        ValidationErrorResponse response = new ValidationErrorResponse(
            "VALIDATION_FAILED",
            "Request validation failed",
            400,
            Instant.now(),
            requestId,
            request.getRequestURI(),
            fieldErrors,
            buildDebugInfo(ex, request, null)
        );

        return ResponseEntity.status(400).body(response);
    }

    /**
     * Handle custom VidTag business exceptions.
     */
    @ExceptionHandler(VidtagException.class)
    public ResponseEntity<ErrorResponse> handleVidtagException(
            VidtagException ex,
            HttpServletRequest request) {

        String requestId = generateRequestId();

        ErrorResponse response = new ErrorResponse(
            ex.getErrorCode(),
            ex.getMessage(),
            ex.getHttpStatus(),
            Instant.now(),
            requestId,
            request.getRequestURI(),
            buildDebugInfo(ex, request, null)
        );

        // Log appropriately based on status
        if (ex.getHttpStatus() >= 500) {
            log.error("Server error [requestId={}]: {}", requestId, ex.getMessage(), ex);
        } else {
            log.warn("Client error [requestId={}]: {}", requestId, ex.getMessage());
        }

        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    /**
     * Handle external service exceptions with Retry-After header.
     */
    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ErrorResponse> handleExternalServiceException(
            ExternalServiceException ex,
            HttpServletRequest request) {

        String requestId = generateRequestId();
        log.error("External service unavailable [requestId={}]: {}", requestId, ex.getMessage(), ex);

        ErrorResponse response = new ErrorResponse(
            ex.getErrorCode(),
            ex.getMessage(),
            503,
            Instant.now(),
            requestId,
            request.getRequestURI(),
            buildDebugInfo(ex, request, ex.getServiceInfo())
        );

        return ResponseEntity.status(503)
            .header("Retry-After", ex.getRetryAfterSeconds().toString())
            .body(response);
    }

    /**
     * Handle all other unexpected exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        String requestId = generateRequestId();
        log.error("Unexpected error [requestId={}]", requestId, ex);

        ErrorResponse response = new ErrorResponse(
            "INTERNAL_ERROR",
            "An unexpected error occurred",
            500,
            Instant.now(),
            requestId,
            request.getRequestURI(),
            buildDebugInfo(ex, request, null)
        );

        return ResponseEntity.status(500).body(response);
    }

    private Object buildDebugInfo(Exception ex, HttpServletRequest request, Map<String, Object> additionalContext) {
        boolean debugMode = debugModeEnabled || "true".equals(request.getParameter("debug"));

        if (!debugMode) {
            return null;
        }

        // Build detailed debug info when enabled
        Map<String, Object> context = new HashMap<>();
        if (additionalContext != null) {
            context.putAll(additionalContext);
        }

        String stackTrace = ExceptionUtils.getStackTrace(ex);
        if (stackTrace.length() > maxStackTraceLength) {
            stackTrace = stackTrace.substring(0, maxStackTraceLength) + "\n... (truncated)";
        }

        return new DebugInfo(
            ex.getClass().getName(),
            stackTrace,
            context
        );
    }

    private String generateRequestId() {
        return UUID.randomUUID().toString();
    }
}
