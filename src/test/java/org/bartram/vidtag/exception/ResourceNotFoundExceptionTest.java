package org.bartram.vidtag.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResourceNotFoundExceptionTest {

    @Test
    void shouldCreateExceptionWithResourceAndIdentifier() {
        var exception = new ResourceNotFoundException("Collection", "My Videos");

        assertThat(exception.getErrorCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(exception.getMessage()).isEqualTo("Collection 'My Videos' not found");
        assertThat(exception.getHttpStatus()).isEqualTo(404);
    }

    @Test
    void shouldHandleNullIdentifier() {
        var exception = new ResourceNotFoundException("User", null);

        assertThat(exception.getMessage()).isEqualTo("User 'null' not found");
    }
}
