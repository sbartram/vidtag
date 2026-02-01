package org.bartram.vidtag.dto.error;

import java.util.Map;

/**
 * Debug information included in error responses when debug mode is enabled.
 */
public record DebugInfo(String exceptionType, String stackTrace, Map<String, Object> additionalContext) {}
