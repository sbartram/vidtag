package org.bartram.vidtag.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InvalidRequestExceptionTest {

    @Test
    void shouldCreateExceptionWithMessage() {
        var exception = new InvalidRequestException("Invalid playlist format");

        assertThat(exception.errorCode()).isEqualTo("INVALID_REQUEST");
        assertThat(exception.getMessage()).isEqualTo("Invalid playlist format");
        assertThat(exception.httpStatus()).isEqualTo(400);
    }
}
