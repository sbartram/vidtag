package org.bartram.vidtag.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.bartram.vidtag.exception.ExternalServiceException;
import org.bartram.vidtag.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test");
    }

    @Test
    void shouldHandleResourceNotFoundException() {
        var exception = new ResourceNotFoundException("Collection", "My Videos");

        var response = handler.handleVidtagException(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.errorCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(body.message()).isEqualTo("Collection 'My Videos' not found");
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.path()).isEqualTo("/api/v1/test");
        assertThat(body.requestId()).isNotNull();
        assertThat(body.debugInfo()).isNull(); // No debug mode
    }

    @Test
    void shouldHandleExternalServiceException() {
        var exception = new ExternalServiceException("youtube", "YouTube API unavailable", null);

        var response = handler.handleExternalServiceException(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getHeaders().get("Retry-After")).containsExactly("30");
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.errorCode()).isEqualTo("EXTERNAL_SERVICE_UNAVAILABLE");
        assertThat(body.status()).isEqualTo(503);
    }

    @Test
    void shouldHandleValidationErrors() {
        var bindingResult = mock(BindingResult.class);
        var fieldErrors = List.of(new FieldError("request", "playlistInput", "playlistInput is required"));
        when(bindingResult.getFieldErrors()).thenReturn(fieldErrors);

        var exception = new MethodArgumentNotValidException(null, bindingResult);

        var response = handler.handleValidation(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.errorCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.validationErrors()).hasSize(1);
        assertThat(body.validationErrors().get(0).field()).isEqualTo("playlistInput");
    }

    @Test
    void shouldHandleGenericException() {
        var exception = new RuntimeException("Unexpected error");

        var response = handler.handleGenericException(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.errorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.message()).isEqualTo("An unexpected error occurred");
        assertThat(body.status()).isEqualTo(500);
    }

    @Test
    void shouldIncludeDebugInfoWhenDebugParamTrue() {
        request.setParameter("debug", "true");
        var exception = new ResourceNotFoundException("Collection", "Test");

        var response = handler.handleVidtagException(exception, request);

        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.debugInfo()).isNotNull();
    }
}
