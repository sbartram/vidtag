package org.bartram.vidtag.exception;

import java.util.Map;
import lombok.Getter;

/**
 * Exception thrown when an external service (YouTube, Raindrop, Claude) is unavailable.
 * Returns HTTP 503 status.
 */
@Getter
public class ExternalServiceException extends VidtagException {

    private final String serviceName;
    private final Integer retryAfterSeconds;

    public ExternalServiceException(String serviceName, String message, Throwable cause) {
        super("EXTERNAL_SERVICE_UNAVAILABLE", message, 503, cause);
        this.serviceName = serviceName;
        this.retryAfterSeconds = 30; // Default from circuit breaker config
    }

    public Map<String, Object> serviceInfo() {
        return Map.of(
                "service", serviceName,
                "retryAfter", retryAfterSeconds);
    }
}
