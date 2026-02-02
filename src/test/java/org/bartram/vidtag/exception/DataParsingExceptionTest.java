package org.bartram.vidtag.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DataParsingExceptionTest {

    @Test
    void shouldCreateExceptionWithMessageAndCause() {
        var cause = new RuntimeException("Invalid JSON");
        var exception = new DataParsingException("Failed to parse AI response", cause);

        assertThat(exception.errorCode()).isEqualTo("DATA_PARSING_ERROR");
        assertThat(exception.getMessage()).isEqualTo("Failed to parse AI response");
        assertThat(exception.httpStatus()).isEqualTo(500);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
}
