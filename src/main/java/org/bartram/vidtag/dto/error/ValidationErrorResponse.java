package org.bartram.vidtag.dto.error;

import java.time.Instant;
import java.util.List;

/**
 * Error response structure for validation failures.
 * Includes field-level error details.
 */
public record ValidationErrorResponse(
    String errorCode,
    String message,
    int status,
    Instant timestamp,
    String requestId,
    String path,
    List<FieldError> validationErrors,
    Object debugInfo
) {
    /**
     * Individual field validation error.
     */
    public record FieldError(String field, String message) {
    }
}
