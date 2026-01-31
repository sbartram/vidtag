# Error Response DTOs - Structured Error Handling Design

**Created:** 2026-01-29
**Status:** Approved
**Goal:** Implement comprehensive error handling with structured error responses, proper HTTP status codes, debug mode support, and SSE-aware error handling.

---

## Design Decisions

### 1. Error Detail Level
**Choice:** Debug mode toggle via query parameter

- **Production mode** (default): Generic error messages, detailed logs server-side only
- **Debug mode** (`?debug=true`): Includes stack traces, internal error details, circuit breaker states
- Prevents leaking implementation details while allowing troubleshooting when needed

### 2. HTTP Status Codes
**Choice:** Standard REST approach

- `400 Bad Request` - Validation errors, invalid requests
- `404 Not Found` - Resource not found (e.g., collection doesn't exist)
- `502 Bad Gateway / 503 Service Unavailable` - External API failures
- `500 Internal Server Error` - Unexpected errors, parsing failures

### 3. Error Response Structure
**Choice:** Extended custom format with tracking

```json
{
  "errorCode": "COLLECTION_NOT_FOUND",
  "message": "Collection 'My Videos' not found in your Raindrop account",
  "status": 404,
  "timestamp": "2026-01-29T10:30:00Z",
  "requestId": "uuid",
  "path": "/api/v1/playlists/tag",
  "debugInfo": { ... }
}
```

**Benefits:**
- `errorCode` - Machine-readable error identification
- `requestId` - Request tracing across logs
- `path` - Helps debugging when multiple endpoints exist
- `debugInfo` - Conditional detailed information

### 4. Validation Error Handling
**Choice:** Field-level details with all failures

```json
{
  "errorCode": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "status": 400,
  "validationErrors": [
    {"field": "playlistInput", "message": "playlistInput is required"},
    {"field": "raindropCollectionTitle", "message": "raindropCollectionTitle is required"}
  ]
}
```

**Benefits:**
- Client can fix all issues at once
- Clear field-level feedback
- Standard Spring validation integration

### 5. Circuit Breaker Error Exposure
**Choice:** Service-specific messages

```json
{
  "errorCode": "EXTERNAL_SERVICE_UNAVAILABLE",
  "message": "YouTube API is currently unavailable",
  "status": 503,
  "service": "youtube",
  "retryAfter": 30
}
```

**Rationale:** Users configure these integrations themselves, so transparency about which service failed is helpful, not a security risk.

### 6. SSE Error Handling
**Choice:** Dual approach - pre-flight vs streaming errors

**Pre-flight errors** (before streaming starts):
- Validation errors → HTTP 400 with error response body
- Business logic errors → HTTP 404/400 with error response body

**Streaming errors** (during processing):
- Individual video failures → `event: error` with error details, continue processing
- Fatal errors → `event: error` + complete stream

```
event: error
data: {"errorCode": "YOUTUBE_API_FAILED", "message": "...", "recoverable": false}

event: completed
data: {"summary": {...}, "partialFailure": true}
```

### 7. Debug Mode Activation
**Choice:** Query parameter

```
POST /api/v1/playlists/tag?debug=true
```

**Benefits:**
- Simple and explicit
- Visible in logs
- No special client configuration needed

---

## Architecture

### Component Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Controller Layer                         │
│  - Receives requests                                         │
│  - @Validated triggers validation                           │
│  - Pre-flight errors caught by GlobalExceptionHandler       │
│  - Streaming errors become SSE events                        │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    Service Layer                             │
│  - Throws typed VidtagException subclasses                   │
│  - Circuit breaker fallbacks throw ExternalServiceException  │
│  - Business logic throws ResourceNotFoundException, etc      │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│              GlobalExceptionHandler                          │
│  - @RestControllerAdvice                                     │
│  - Maps exceptions → ErrorResponse DTOs                      │
│  - Generates requestId (UUID)                                │
│  - Checks ?debug=true for debugInfo                          │
│  - Logs appropriately (error vs warn)                        │
└─────────────────────────────────────────────────────────────┘
```

### Core DTOs

**ErrorResponse** - Base error structure
```java
public record ErrorResponse(
    String errorCode,
    String message,
    int status,
    Instant timestamp,
    String requestId,
    String path,
    Object debugInfo
) {
    public static ErrorResponse of(
        String errorCode,
        String message,
        int status,
        String requestId,
        String path
    ) {
        return new ErrorResponse(errorCode, message, status,
            Instant.now(), requestId, path, null);
    }
}
```

**ValidationErrorResponse** - For validation failures
```java
public record ValidationErrorResponse(
    String errorCode,
    String message,
    int status,
    Instant timestamp,
    String requestId,
    String path,
    List<FieldError> validationErrors,
    Object debugInfo
) {
    public record FieldError(String field, String message) {}
}
```

**DebugInfo** - Detailed error information (only when `?debug=true`)
```java
public record DebugInfo(
    String exceptionType,
    String stackTrace,
    Map<String, Object> additionalContext
) {}
```

### Exception Hierarchy

**Base Exception**
```java
public abstract class VidtagException extends RuntimeException {
    private final String errorCode;
    private final int httpStatus;

    protected VidtagException(String errorCode, String message, int httpStatus);
    protected VidtagException(String errorCode, String message, int httpStatus, Throwable cause);

    public String getErrorCode();
    public int getHttpStatus();
}
```

**Specific Exception Types**

| Exception | Error Code | HTTP Status | Use Case |
|-----------|------------|-------------|----------|
| `ResourceNotFoundException` | `RESOURCE_NOT_FOUND` | 404 | Collection not found, etc |
| `ExternalServiceException` | `EXTERNAL_SERVICE_UNAVAILABLE` | 503 | Circuit breaker failures |
| `InvalidRequestException` | `INVALID_REQUEST` | 400 | Business logic violations |
| `DataParsingException` | `DATA_PARSING_ERROR` | 500 | JSON parsing, duration parsing |

**ExternalServiceException** - Enhanced with retry info
```java
public class ExternalServiceException extends VidtagException {
    private final String serviceName;  // "youtube", "raindrop", "claude"
    private final Integer retryAfterSeconds;  // from circuit breaker config

    public Map<String, Object> getServiceInfo() {
        return Map.of(
            "service", serviceName,
            "retryAfter", retryAfterSeconds
        );
    }
}
```

### Global Exception Handler

**@RestControllerAdvice** - Centralized exception mapping

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${vidtag.debug-mode:false}")
    private boolean debugModeEnabled;

    // Handle validation errors (400)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(...);

    // Handle custom business exceptions
    @ExceptionHandler(VidtagException.class)
    public ResponseEntity<ErrorResponse> handleVidtagException(...);

    // Handle external service failures with Retry-After header
    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ErrorResponse> handleExternalService(...);

    // Handle unexpected errors (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(...);

    private Object buildDebugInfo(Exception ex, HttpServletRequest request);
    private String generateRequestId();
}
```

**Debug Info Logic:**
- Check `request.getParameter("debug")` OR global `vidtag.debug-mode` config
- If enabled, include: exception type, stack trace (truncated), additional context
- Stack traces limited to 2000 chars (configurable)

### Service Layer Updates

**Pattern:** Replace `throw new RuntimeException(...)` with typed exceptions

**Before:**
```java
private List<RaindropTag> getUserTagsFallback(String userId, Throwable throwable) {
    throw new RuntimeException("Failed to fetch Raindrop tags after retries", throwable);
}
```

**After:**
```java
private List<RaindropTag> getUserTagsFallback(String userId, Throwable throwable) {
    throw new ExternalServiceException("raindrop",
        "Raindrop API is currently unavailable", throwable);
}
```

**Updates Required:**
- `RaindropService.java` - 5 fallback methods, `resolveCollectionId` null check
- `YouTubeService.java` - 1 fallback method
- `VideoTaggingService.java` - 1 fallback method, JSON parsing errors

### SSE Error Handling

**ProgressEvent Enhancement:**
```java
public static ProgressEvent error(String errorCode, String message, boolean recoverable) {
    return new ProgressEvent("error", message, Map.of(
        "errorCode", errorCode,
        "recoverable", recoverable
    ));
}

public static ProgressEvent fatalError(String errorCode, String message) {
    return error(errorCode, message, false);
}
```

**Controller Pre-flight Check:**
```java
@PostMapping(value = "/tag", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public ResponseEntity<?> tagPlaylist(
        @Validated @RequestBody TagPlaylistRequest request,
        HttpServletRequest httpRequest) {

    try {
        // Pre-flight business validation
        validatePreFlightConditions(request);

        // Start SSE streaming
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        // ... setup and start processing

        return ResponseEntity.ok(emitter);

    } catch (VidtagException ex) {
        // Pre-flight errors return HTTP error response
        throw ex;  // GlobalExceptionHandler catches
    }
}
```

**Orchestrator Error Events:**
```java
try {
    // process video
} catch (ExternalServiceException ex) {
    eventEmitter.accept(ProgressEvent.error(
        ex.getErrorCode(),
        ex.getMessage(),
        true  // recoverable
    ));
    return new VideoProcessingResult(..., ProcessingStatus.FAILED, ex.getMessage());
}
```

---

## Configuration

### application.yaml
```yaml
vidtag:
  # Enable debug mode globally (overridden by ?debug=true param)
  debug-mode: ${DEBUG_MODE:false}

  # Error response configuration
  error:
    include-stack-trace: ${INCLUDE_STACK_TRACE:false}
    include-binding-errors: true
    max-stack-trace-length: 2000
```

### Dependencies

Add to `build.gradle.kts`:
```kotlin
implementation("org.apache.commons:commons-lang3:3.14.0")  // For ExceptionUtils.getStackTrace()
```

(Already has `spring-boot-starter-validation` for `@Validated` support)

---

## Error Code Catalog

| Error Code | HTTP Status | Description | Example Scenario |
|------------|-------------|-------------|------------------|
| `VALIDATION_FAILED` | 400 | Request validation failed | Blank playlistInput |
| `INVALID_REQUEST` | 400 | Business logic violation | Invalid playlist format |
| `RESOURCE_NOT_FOUND` | 404 | Resource doesn't exist | Collection not found |
| `EXTERNAL_SERVICE_UNAVAILABLE` | 503 | Circuit breaker open | YouTube API down |
| `DATA_PARSING_ERROR` | 500 | Failed to parse data | Invalid JSON from Claude |
| `INTERNAL_ERROR` | 500 | Unexpected error | Uncaught exception |

---

## Testing Strategy

### Unit Tests
- Test each exception type construction
- Test GlobalExceptionHandler for each exception type
- Verify debug mode logic (with/without `?debug=true`)
- Verify validation error field aggregation

### Integration Tests
- Test validation errors return 400 with field details
- Test 404 for non-existent collection
- Test 503 for circuit breaker failures (mock external APIs)
- Test SSE error events during streaming
- Test debug mode query parameter

### Manual Testing
- Verify error responses in browser/Postman
- Verify SSE error events in EventSource client
- Test with real circuit breaker failures (stop Redis, etc)

---

## Implementation Plan

Implementation will follow TDD approach with the following sequence:

1. **Exception hierarchy** - Base class and specific types
2. **Error response DTOs** - ErrorResponse, ValidationErrorResponse, DebugInfo
3. **GlobalExceptionHandler** - Exception mapping and debug mode logic
4. **Service layer updates** - Replace RuntimeException with typed exceptions
5. **SSE error handling** - ProgressEvent updates, controller pre-flight checks
6. **Configuration** - application.yaml properties
7. **Integration tests** - End-to-end error handling verification

---

## Benefits

### Developer Experience
- **Clear error codes** - Machine-readable for client-side error handling
- **Field-level validation** - Fix all issues at once
- **Request tracing** - requestId ties client errors to server logs
- **Debug mode** - Troubleshoot without redeployment

### Operations
- **Proper logging** - Errors vs warnings based on HTTP status
- **Service monitoring** - Track which external services are failing
- **Retry guidance** - Retry-After header for 503 responses

### API Consumers
- **Consistent format** - All errors follow same structure
- **Actionable messages** - Clear guidance on what went wrong
- **Partial success** - SSE allows processing to continue after recoverable errors
- **Standards-based** - Follows REST conventions for HTTP status codes
