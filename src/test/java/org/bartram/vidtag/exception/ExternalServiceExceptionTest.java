package org.bartram.vidtag.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExternalServiceExceptionTest {

    @Test
    void shouldCreateExceptionWithServiceNameAndMessage() {
        var cause = new RuntimeException("Connection timeout");
        var exception = new ExternalServiceException("youtube", "YouTube API is currently unavailable", cause);

        assertThat(exception.getErrorCode()).isEqualTo("EXTERNAL_SERVICE_UNAVAILABLE");
        assertThat(exception.getMessage()).isEqualTo("YouTube API is currently unavailable");
        assertThat(exception.getHttpStatus()).isEqualTo(503);
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.getServiceName()).isEqualTo("youtube");
        assertThat(exception.getRetryAfterSeconds()).isEqualTo(30);
    }

    @Test
    void shouldProvideServiceInfoMap() {
        var exception = new ExternalServiceException("raindrop", "Raindrop API unavailable", null);

        var serviceInfo = exception.getServiceInfo();

        assertThat(serviceInfo).containsEntry("service", "raindrop");
        assertThat(serviceInfo).containsEntry("retryAfter", 30);
    }
}
