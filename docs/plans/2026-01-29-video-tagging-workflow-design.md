# Video Tagging Workflow Design

**Date:** 2026-01-29  
**Status:** Approved

## Overview

Design for the core video tagging feature: retrieve YouTube playlist videos, use Claude AI to determine appropriate tags from existing Raindrop.io tags, and save each video as a bookmark in Raindrop.io.

---

## 1. API Design

### REST Endpoint
```
POST /api/v1/playlists/tag
```

### Request Body
```json
{
  "playlistInput": "PLxxx or https://youtube.com/playlist?list=PLxxx",
  "raindropCollectionTitle": "My Videos",
  "filters": {
    "publishedAfter": "2024-01-01T00:00:00Z",
    "maxDuration": 3600,
    "maxVideos": 50
  },
  "tagStrategy": {
    "maxTagsPerVideo": 5,
    "confidenceThreshold": 0.7,
    "customInstructions": "Focus on technical topics"
  },
  "verbosity": "DETAILED"
}
```

### Response
Server-Sent Events stream with content-type `text/event-stream`. 

**Event Types:**
- `started` - Processing begun
- `progress` - Video being processed (includes video title, current tags, status based on verbosity)
- `video_completed` - Video saved/skipped
- `batch_completed` - Batch of 10 finished
- `error` - Non-fatal error occurred
- `completed` - Final summary (total processed, succeeded, skipped, failed)

The endpoint validates inputs, starts processing, and immediately begins streaming events. Client receives real-time updates as each video moves through the pipeline.

---

## 2. Service Architecture

### Core Services

**`PlaylistTaggingController`**
- Handles POST endpoint, validates request
- Initiates SSE stream using Spring's `SseEmitter`
- Delegates to orchestrator service asynchronously

**`VideoTaggingOrchestrator`**
- Coordinates the entire tagging workflow
- Emits SSE events at each stage based on verbosity level
- Handles batch processing (10 videos at a time)
- Applies filters and manages retry logic

**`YouTubeService`**
- Fetches playlist details and video list from YouTube API
- Extracts video metadata (title, description, published date, duration)
- Applies filters (publishedAfter, maxDuration, maxVideos)
- Uses circuit breaker for YouTube API calls

**`RaindropService`**
- Fetches user's collections and resolves collection title to ID
- Fetches existing tags from Raindrop.io (cached with Spring Cache)
- Checks if video URL already exists in collection
- Creates new bookmarks with tags
- Uses circuit breaker for Raindrop API calls

**`VideoTaggingService`**
- Integrates with Spring AI chat client
- Sends video metadata + existing tags to Claude
- Implements hybrid tagging strategy (prefer existing, allow new if confident)
- Applies maxTagsPerVideo and confidenceThreshold

All services are Spring beans with `@Service` annotation. The orchestrator uses `@Async` to run processing off the request thread. Circuit breakers (Resilience4J) wrap all external API calls with retry policies.

---

## 3. Processing Flow

### Step-by-step Workflow

1. **Request Validation** - Controller validates input, normalizes playlist ID from URL if needed, creates SSE emitter

2. **Initialization** - Orchestrator emits `started` event, resolves collection title to ID, fetches and caches Raindrop tags list (TTL: 15 minutes)

3. **Fetch Playlist** - YouTubeService retrieves playlist videos, applies filters (publishedAfter, maxDuration, maxVideos), emits progress event

4. **Batch Processing** - Videos split into batches of 10. For each batch:
   - Process videos sequentially within the batch
   - Emit `batch_started` event

5. **Per-Video Processing:**
   - Check if URL exists in Raindrop → skip if found (emit `video_skipped`)
   - Send video metadata + cached tags to Claude AI
   - Claude returns recommended tags with confidence scores
   - Apply hybrid strategy: use existing tags where possible, add new tags only if confidence > threshold
   - Save bookmark to Raindrop with selected tags
   - Emit `video_completed` with tags (based on verbosity)

6. **Error Handling** - If video fails after retries, emit `error` event, continue to next video

7. **Completion** - After all batches, emit `completed` event with summary: total videos, succeeded, skipped, failed

The flow maintains state in memory during processing, with SSE keeping the connection alive throughout.

---

## 4. Error Handling & Resilience

### Circuit Breaker Configuration (Resilience4J)

Each external API (YouTube, Raindrop, Claude AI) gets its own circuit breaker:
- **Failure threshold:** 50% failures in 10 calls opens circuit
- **Wait duration:** 30 seconds in open state before trying again
- **Permitted calls in half-open:** 3 calls to test recovery
- Circuit breaker state changes emit events to SSE stream (if verbosity allows)

### Retry Strategy

**YouTube API failures:**
- 3 retry attempts with exponential backoff (1s, 2s, 4s)
- Retryable: rate limits, timeouts, 5xx errors
- Non-retryable: 404 playlist not found, authentication errors

**Claude AI failures:**
- 2 retry attempts with backoff
- Retryable: timeouts, overloaded errors
- Non-retryable: invalid API key, content policy violations

**Raindrop API failures:**
- 3 retry attempts
- Retryable: rate limits, timeouts
- Non-retryable: collection not found, authentication errors

### Failure Handling
- Video-level failures skip that video, continue processing others
- Playlist-level failures (can't fetch playlist) fail entire request
- All failures logged and included in final summary
- Critical errors close SSE stream with error event

---

## 5. Caching Strategy

### Raindrop Tags Cache

Using Spring Cache abstraction with Redis as the cache provider:

```java
@Cacheable(value = "raindrop-tags", key = "#userId")
public List<Tag> getUserTags(String userId) {
    // Fetch from Raindrop API
}
```

**Configuration:**
- **TTL:** 15 minutes - balances freshness with API call reduction
- **Cache key:** User ID (supports multi-user scenarios)
- **Eviction policy:** LRU (Least Recently Used)
- **Max cache size:** 1000 entries

**Cache Refresh:**
- Automatic expiry after 15 minutes
- Manual refresh available via `@CacheEvict` if user adds tags outside this system
- Cold start: first request fetches from API, subsequent requests use cache

**Why only cache tags:**
- YouTube video metadata changes (view counts, etc.) - skip caching for freshness
- AI decisions should adapt to context - skip caching to avoid stale recommendations
- Raindrop tags are relatively stable user data - perfect candidate for caching

**Cache Monitoring:**
Spring Boot Actuator exposes cache metrics (hit rate, evictions) at `/actuator/metrics/cache.gets`

---

## 6. Data Models

### Request/Response DTOs

```java
record TagPlaylistRequest(
    String playlistInput,
    String raindropCollectionTitle,
    VideoFilters filters,
    TagStrategy tagStrategy,
    Verbosity verbosity
)

record VideoFilters(
    Instant publishedAfter,
    Integer maxDuration,
    Integer maxVideos
)

record TagStrategy(
    Integer maxTagsPerVideo,
    Double confidenceThreshold,
    String customInstructions
)

enum Verbosity { MINIMAL, STANDARD, DETAILED, VERBOSE }
```

### Domain Models

```java
record VideoMetadata(
    String videoId,
    String url,
    String title,
    String description,
    Instant publishedAt,
    Integer duration
)

record TagWithConfidence(
    String tag,
    Double confidence,
    Boolean isExisting  // true if from Raindrop, false if AI-suggested
)

record VideoProcessingResult(
    VideoMetadata video,
    List<TagWithConfidence> selectedTags,
    ProcessingStatus status,  // SUCCESS, SKIPPED, FAILED
    String errorMessage
)

record ProcessingSummary(
    Integer totalVideos,
    Integer succeeded,
    Integer skipped,
    Integer failed
)
```

These models are immutable Java records (Java 21 feature), providing clean data transfer and domain representation.

---

## 7. Testing Strategy

### Integration Tests (using Testcontainers)

**`VideoTaggingIntegrationTest`**
- Starts Redis container via `@ServiceConnection`
- Mocks YouTube, Raindrop, and Claude AI clients
- Tests full workflow: request → SSE events → final summary
- Validates batch processing and event ordering
- Verifies cache population and usage

**`CircuitBreakerTest`**
- Simulates API failures to trigger circuit breakers
- Verifies retry attempts and exponential backoff
- Ensures proper state transitions (closed → open → half-open)
- Tests that processing continues after video-level failures

**`YouTubeServiceTest`**
- Mocks YouTube API responses
- Tests playlist parsing (both ID and URL formats)
- Validates filtering logic (publishedAfter, maxDuration, maxVideos)

**`VideoTaggingServiceTest`**
- Mocks Spring AI chat client
- Tests hybrid tagging strategy (existing vs. new tags)
- Validates confidence threshold filtering
- Tests maxTagsPerVideo enforcement

**`RaindropServiceTest`**
- Mocks Raindrop API
- Tests duplicate detection
- Validates cache behavior with different TTLs

**Test Utilities:**
- Mock SSE emitter to capture and verify events
- Sample data builders for playlists and videos
- Assertion helpers for event streams

All tests run with `./gradlew test` using the Testcontainers configuration.

---

## Implementation Notes

- Uses Spring Boot 4.0.2 with Java 21
- Leverages Spring AI for Claude integration
- Circuit breakers via Spring Cloud Circuit Breaker (Resilience4J)
- Redis caching via Spring Cache
- Traditional service layer architecture (not reactive)
- SSE for real-time streaming without WebFlux complexity
