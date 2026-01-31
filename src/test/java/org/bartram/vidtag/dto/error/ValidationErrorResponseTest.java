package org.bartram.vidtag.dto.error;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationErrorResponseTest {

    @Test
    void shouldCreateValidationErrorResponse() {
        var fieldErrors = List.of(
            new ValidationErrorResponse.FieldError("email", "Email is required"),
            new ValidationErrorResponse.FieldError("name", "Name must not be blank")
        );

        var response = new ValidationErrorResponse(
            "VALIDATION_FAILED",
            "Request validation failed",
            400,
            Instant.now(),
            "req-456",
            "/api/test",
            fieldErrors,
            null
        );

        assertThat(response.errorCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.validationErrors()).hasSize(2);
        assertThat(response.validationErrors().get(0).field()).isEqualTo("email");
        assertThat(response.validationErrors().get(0).message()).isEqualTo("Email is required");
    }
}
