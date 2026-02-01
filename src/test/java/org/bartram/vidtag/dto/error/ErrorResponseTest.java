package org.bartram.vidtag.dto.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ErrorResponseTest {

    @Test
    void shouldCreateErrorResponseWithAllFields() {
        var timestamp = Instant.now();
        var debugInfo = new DebugInfo("RuntimeException", "stack trace here", null);

        var response =
                new ErrorResponse("TEST_ERROR", "Test message", 400, timestamp, "req-123", "/api/test", debugInfo);

        assertThat(response.errorCode()).isEqualTo("TEST_ERROR");
        assertThat(response.message()).isEqualTo("Test message");
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.timestamp()).isEqualTo(timestamp);
        assertThat(response.requestId()).isEqualTo("req-123");
        assertThat(response.path()).isEqualTo("/api/test");
        assertThat(response.debugInfo()).isEqualTo(debugInfo);
    }

    @Test
    void shouldCreateErrorResponseWithoutDebugInfo() {
        var response = ErrorResponse.of("TEST_ERROR", "Test message", 400, "req-123", "/api/test");

        assertThat(response.errorCode()).isEqualTo("TEST_ERROR");
        assertThat(response.message()).isEqualTo("Test message");
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.requestId()).isEqualTo("req-123");
        assertThat(response.path()).isEqualTo("/api/test");
        assertThat(response.debugInfo()).isNull();
        assertThat(response.timestamp()).isNotNull();
    }
}
