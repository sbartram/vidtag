package org.bartram.vidtag.dto.error;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DebugInfoTest {

    @Test
    void shouldCreateDebugInfo() {
        Map<String, Object> context = Map.of("circuitState", "OPEN", "service", "youtube");

        var debugInfo = new DebugInfo(
            "ExternalServiceException",
            "stack trace line 1\nstack trace line 2",
            context
        );

        assertThat(debugInfo.exceptionType()).isEqualTo("ExternalServiceException");
        assertThat(debugInfo.stackTrace()).contains("stack trace line 1");
        assertThat(debugInfo.additionalContext()).containsEntry("circuitState", "OPEN");
    }
}
