package org.bartram.vidtag.dto.error;

import java.time.Instant;

/**
 * Standard error response structure for all API errors.
 */
public record ErrorResponse(
        String errorCode,
        String message,
        int status,
        Instant timestamp,
        String requestId,
        String path,
        Object debugInfo) {
    /**
     * Creates an error response without debug info.
     */
    public static ErrorResponse of(String errorCode, String message, int status, String requestId, String path) {
        return new ErrorResponse(errorCode, message, status, Instant.now(), requestId, path, null);
    }
}
