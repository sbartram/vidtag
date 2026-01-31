package org.bartram.vidtag.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvalidRequestExceptionTest {

    @Test
    void shouldCreateExceptionWithMessage() {
        var exception = new InvalidRequestException("Invalid playlist format");

        assertThat(exception.getErrorCode()).isEqualTo("INVALID_REQUEST");
        assertThat(exception.getMessage()).isEqualTo("Invalid playlist format");
        assertThat(exception.getHttpStatus()).isEqualTo(400);
    }
}
