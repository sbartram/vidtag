package org.bartram.vidtag.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VidtagExceptionTest {

    @Test
    void shouldStoreErrorCodeAndHttpStatus() {
        var exception = new TestVidtagException("TEST_ERROR", "Test message", 400);

        assertThat(exception.getErrorCode()).isEqualTo("TEST_ERROR");
        assertThat(exception.getMessage()).isEqualTo("Test message");
        assertThat(exception.getHttpStatus()).isEqualTo(400);
    }

    @Test
    void shouldStoreErrorCodeHttpStatusAndCause() {
        var cause = new RuntimeException("Root cause");
        var exception = new TestVidtagException("TEST_ERROR", "Test message", 500, cause);

        assertThat(exception.getErrorCode()).isEqualTo("TEST_ERROR");
        assertThat(exception.getMessage()).isEqualTo("Test message");
        assertThat(exception.getHttpStatus()).isEqualTo(500);
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    // Test implementation for testing abstract class
    private static class TestVidtagException extends VidtagException {
        public TestVidtagException(String errorCode, String message, int httpStatus) {
            super(errorCode, message, httpStatus);
        }

        public TestVidtagException(String errorCode, String message, int httpStatus, Throwable cause) {
            super(errorCode, message, httpStatus, cause);
        }
    }
}
