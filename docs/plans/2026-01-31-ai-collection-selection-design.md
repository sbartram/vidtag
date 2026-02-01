# AI-Powered Collection Selection Design

**Date:** 2026-01-31
**Status:** Approved
**Breaking Change:** Yes - removes `raindropCollectionTitle` from API

## Overview

Enable VidTag to automatically determine the appropriate Raindrop.io collection for each YouTube playlist using AI analysis, eliminating the need for manual collection specification in API calls or hardcoded defaults in the scheduler.

## Requirements

- AI analyzes playlist metadata and sample videos to choose the best collection
- Choose from existing Raindrop collections only (no auto-creation except fallback)
- Use configurable fallback collection when AI confidence is low
- Cache playlist → collection mappings to reduce AI API calls
- Cache user's collection list to reduce Raindrop API calls
- Auto-create fallback collection if it doesn't exist
- Remove `raindropCollectionTitle` field from API (breaking change)
- All configuration values should be customizable via environment variables

## Design Decisions

### 1. Approach: Per-Playlist Collection Selection

**Decision:** AI determines one collection per playlist (not per video)

**Rationale:**
- Keeps all videos from a playlist together in one collection
- Only one AI call per playlist (vs. one per video)
- Lower cost and latency than per-video analysis
- Simpler mental model: "this playlist maps to that collection"
- Playlists typically have coherent themes

**Trade-off:** Less flexible than per-video selection, but more predictable and efficient

### 2. Remove Collection Field Entirely

**Decision:** Remove `raindropCollectionTitle` from `TagPlaylistRequest` (no optional field)

**Rationale:**
- Simpler code - single code path for all requests
- Consistent behavior across API and scheduler
- Forces full automation (no manual overrides)
- Cleaner API contract

**Alternative rejected:** Making field optional (hybrid approach)
- Would require maintaining two code paths
- Increases complexity for marginal flexibility benefit
- Users who need manual control can configure the fallback

### 3. AI Input Data

**Decision:** Provide playlist metadata + titles of first 5-10 videos

**Rationale:**
- Playlist title/description gives high-level context
- Sample video titles show actual content diversity
- 5-10 videos is enough to identify themes without overloading prompt
- Minimal API calls (one playlist metadata fetch, one paginated video fetch)

**Alternatives rejected:**
- Just playlist metadata: Too little context, playlist titles can be vague
- Full video metadata: Unnecessary detail, longer prompts, no clear benefit

### 4. Caching Strategy

**Decision:** Three-tier caching approach

**Cache 1: Playlist → Collection Mapping**
- Key: `"playlist:collection:{playlistId}"`
- Value: Collection title (String)
- TTL: Configurable (default 24 hours)
- Purpose: Avoid re-analyzing same playlist repeatedly

**Cache 2: User's Collections List**
- Key: `"raindrop:collections"`
- Value: List of collection titles
- TTL: Configurable (default 1 hour)
- Purpose: Reduce Raindrop API calls when checking available collections
- Invalidation: Evict after creating fallback collection

**Cache 3: Collection Title → ID** (existing)
- Already implemented in `RaindropService.resolveCollectionId()`
- TTL: 15 minutes
- No changes needed

**Rationale:**
- Scheduler runs hourly - without caching, same playlist analyzed 24 times/day
- Collections rarely change - safe to cache for hours
- Aligns with existing caching pattern (tags cached 15 min)
- Significant cost reduction for AI and Raindrop API calls

### 5. Fallback Strategy

**Decision:** Use configurable fallback collection when AI confidence is low

**Fallback Triggers:**
- AI explicitly returns "LOW_CONFIDENCE"
- AI returns invalid collection name (not in user's list)
- Circuit breaker open for Claude API
- YouTube API fails to fetch playlist metadata
- Empty playlist (no videos to analyze)

**Fallback Configuration:**
- Property: `vidtag.raindrop.fallback-collection`
- Default: `"Videos"`
- Environment variable: `VIDTAG_RAINDROP_FALLBACK_COLLECTION`

**Auto-creation:**
- If fallback collection doesn't exist, create it automatically
- Prevents processing failures due to missing collections
- Invalidate collections list cache after creation

**Alternative rejected:** Fail playlist processing when uncertain
- Would require manual intervention
- Reduces automation benefits
- Fallback provides graceful degradation

### 6. Configuration Properties

**Decision:** Make all timeouts and names configurable

**New properties in `application.yaml`:**
```yaml
vidtag:
  raindrop:
    fallback-collection: ${VIDTAG_RAINDROP_FALLBACK_COLLECTION:Videos}
    collection-cache-ttl: ${VIDTAG_RAINDROP_COLLECTION_CACHE_TTL:24h}
    collections-list-cache-ttl: ${VIDTAG_RAINDROP_COLLECTIONS_LIST_CACHE_TTL:1h}
```

**New Configuration Class: `RaindropProperties.java`**
```java
@ConfigurationProperties(prefix = "vidtag.raindrop")
public class RaindropProperties {
    private String fallbackCollection = "Videos";
    private Duration collectionCacheTtl = Duration.ofHours(24);
    private Duration collectionsListCacheTtl = Duration.ofHours(1);
    // getters/setters
}
```

**Rationale:**
- Users can tune cache TTLs based on their needs
- Fallback collection name is user preference
- Follows existing configuration pattern (environment variable overrides)

## Architecture

### New Component: CollectionSelectionService

**Responsibilities:**
1. Fetch available Raindrop collections (cached)
2. Fetch playlist metadata and sample videos
3. Call Claude AI to determine best collection
4. Handle caching of playlist → collection mappings
5. Apply fallback logic when AI confidence is low
6. Create fallback collection if it doesn't exist

**Dependencies:**
- `RaindropService` - fetch/create collections, resolve collection IDs
- `YouTubeService` - fetch playlist metadata and videos
- `ChatClient` (Spring AI) - interact with Claude API
- `CacheManager` (Spring Cache) - Redis caching
- `RaindropProperties` - configuration values

**Integration Point:**

`VideoTaggingOrchestrator.processPlaylist()` flow changes:

**Before:**
```
Request → Extract collection title → Resolve to ID → Process videos
```

**After:**
```
Request → Extract playlist ID → Select collection via AI → Resolve to ID → Process videos
                                  ↓
                    CollectionSelectionService
```

### API Contract Changes

**TagPlaylistRequest.java - BREAKING CHANGE**

**Before:**
```java
public record TagPlaylistRequest(
    @NotBlank String playlistInput,
    @NotBlank String raindropCollectionTitle,  // REMOVED
    VideoFilters filters,
    TagStrategy tagStrategy,
    Verbosity verbosity
)
```

**After:**
```java
public record TagPlaylistRequest(
    @NotBlank String playlistInput,
    // raindropCollectionTitle field removed - AI determines collection
    VideoFilters filters,
    TagStrategy tagStrategy,
    Verbosity verbosity
)
```

### Scheduler Changes

**PlaylistProcessingScheduler.java**

**Before:**
```java
private static final String DEFAULT_COLLECTION = "Videos";

TagPlaylistRequest request = new TagPlaylistRequest(
    playlistId,
    DEFAULT_COLLECTION,  // Hardcoded
    new VideoFilters(null, null, null),
    TagStrategy.SUGGEST,
    null
);
```

**After:**
```java
// DEFAULT_COLLECTION constant removed

TagPlaylistRequest request = new TagPlaylistRequest(
    playlistId,
    // No collection field - AI determines it
    new VideoFilters(null, null, null),
    TagStrategy.SUGGEST,
    null
);
```

## Data Flow

**End-to-end flow when `VideoTaggingOrchestrator.processPlaylist()` is called:**

1. **Extract playlist ID** from request input (URL or ID)

2. **Select collection** via `CollectionSelectionService.selectCollection(playlistId)`:

   a. **Check playlist → collection cache**
      - Cache key: `"playlist:collection:{playlistId}"`
      - If **cache hit:** Return cached collection title, skip to step 3
      - If **cache miss:** Continue to step 2b

   b. **Fetch available collections** via `raindropService.getUserCollections()`
      - **Cached in Redis** with configurable TTL (default 1 hour)
      - Cache key: `"raindrop:collections"`
      - Cache miss: Call Raindrop API and cache result

   c. **Gather AI input data:**
      - Fetch playlist metadata: `youtubeService.getPlaylistMetadata(playlistId)`
      - Fetch sample videos: `youtubeService.getPlaylistVideos(playlistId, limit=10)`
      - Extract titles from first 5-10 videos

   d. **Call Claude AI** with constructed prompt:
      - Provide: available collections, playlist metadata, sample video titles
      - Expect: collection name OR "LOW_CONFIDENCE"

   e. **Apply selection logic:**
      - If valid collection name in user's list: Use it
      - If "LOW_CONFIDENCE" or invalid name: Use configured fallback
      - If fallback doesn't exist: Create it via `raindropService.createCollection()`
        - Invalidate `"raindrop:collections"` cache after creation
      - **Cache the decision** in Redis with configured TTL (default 24 hours)

3. **Resolve collection to ID** via `raindropService.resolveCollectionId(collectionTitle)`
   - Uses existing cache (15 min TTL)

4. **Process videos** (existing flow continues unchanged)
   - Fetch all videos, batch processing, tag generation, bookmark creation

## AI Prompt Design

### Prompt Structure

```
You are helping categorize YouTube videos into Raindrop.io collections.

Available collections:
- Technology
- Cooking
- Fitness
- Travel
- Finance
- Education
[... all user's collections ...]

Playlist information:
Title: "Advanced Java Programming Tutorials"
Description: "Learn Spring Boot, microservices, and cloud deployment with hands-on examples"

Sample video titles:
1. "Spring Boot REST API Tutorial - Building Production APIs"
2. "Microservices Architecture Explained - Best Practices"
3. "Docker for Java Developers - Complete Guide"
4. "Kubernetes Deployment for Spring Boot Apps"
5. "Testing Microservices with JUnit and Mockito"
6. "Spring Security OAuth2 Implementation"
7. "Building Cloud-Native Java Applications"
8. "Event-Driven Architecture with Kafka"
9. "GraphQL API with Spring Boot"
10. "Monitoring Java Apps with Prometheus"

Choose the most appropriate collection from the available collections above for this playlist.

Rules:
- Respond with ONLY the exact collection name from the list (e.g., "Technology")
- If none of the collections are a good fit, respond with exactly "LOW_CONFIDENCE"
- Do not create new collection names
- Do not explain your reasoning
- Match the collection name exactly as shown in the list

Response:
```

### Expected Response Formats

**High confidence - valid collection:**
```
Technology
```

**Low confidence - no good match:**
```
LOW_CONFIDENCE
```

### Response Parsing Logic

```java
String response = chatClient.call(prompt).trim();

if ("LOW_CONFIDENCE".equals(response)) {
    return fallbackCollection;
}

if (availableCollections.contains(response)) {
    return response;
}

// Invalid response (not in available collections)
log.warn("AI suggested non-existent collection '{}', using fallback", response);
return fallbackCollection;
```

## Error Handling

### Scenario 1: AI Service Unavailable (Circuit Breaker Open)

**Trigger:** Claude API circuit breaker in OPEN state

**Behavior:**
- Skip AI call, use fallback collection immediately
- Log: `WARN "Circuit breaker open for Claude, using fallback collection '{name}'"`
- **Do NOT cache** the fallback decision (allow retry when circuit closes)

**Rationale:** Temporary failures shouldn't result in long-term cached fallback decisions

### Scenario 2: AI Returns Invalid Collection Name

**Trigger:** AI responds with collection name not in user's available collections

**Example:** AI returns `"Tech Stuff"` but only `"Technology"` exists

**Behavior:**
- Log: `WARN "AI suggested non-existent collection 'Tech Stuff', using fallback '{fallback}'"`
- Use fallback collection
- **Cache the fallback decision** (avoid repeated invalid suggestions for same playlist)

**Rationale:** Likely a persistent issue with playlist content vs. available collections

### Scenario 3: Fallback Collection Doesn't Exist

**Trigger:** Configured fallback collection not in user's Raindrop account

**Behavior:**
1. Log: `INFO "Fallback collection '{name}' does not exist, creating it"`
2. Call: `raindropService.createCollection(fallbackName)`
3. Invalidate: `"raindrop:collections"` cache (refresh collections list)
4. If creation fails: Throw exception, fail playlist processing with clear error message

**Rationale:** Auto-create for convenience, but don't silently swallow creation failures

### Scenario 4: YouTube API Fails (Playlist Metadata)

**Trigger:** `youtubeService.getPlaylistMetadata()` throws exception

**Behavior:**
- Cannot determine collection (need playlist context)
- Propagate exception up to orchestrator
- Orchestrator emits error event via SSE
- User sees clear error: `"Failed to fetch playlist metadata: {error}"`

**Rationale:** Can't make informed decision without basic playlist info

### Scenario 5: Empty Playlist

**Trigger:** Playlist exists but contains zero videos

**Behavior:**
- Log: `INFO "Empty playlist '{id}', using fallback collection '{fallback}'"`
- Use fallback collection (not enough context for AI)
- Cache the fallback decision

**Rationale:** No content to analyze, fallback is reasonable default

### Scenario 6: Redis Cache Unavailable

**Trigger:** Redis connection failure or timeout

**Behavior:**
- Spring Cache abstraction handles gracefully (cache-aside pattern)
- System continues without caching (calls AI every time)
- Existing circuit breakers and retries apply to dependent services

**Rationale:** Degraded performance is better than complete failure

## Testing Strategy

### Unit Tests

**CollectionSelectionServiceTest.java:**

| Test Method | Purpose |
|-------------|---------|
| `selectCollection_cacheHit_returnsCachedValue()` | Verify cache lookup works, no AI call when cached |
| `selectCollection_cacheMiss_callsAI()` | Verify AI invoked on cache miss |
| `selectCollection_aiReturnsValidCollection_cachesAndReturns()` | Happy path: valid response cached and returned |
| `selectCollection_aiReturnsLowConfidence_usesFallback()` | Verify fallback logic when AI uncertain |
| `selectCollection_aiReturnsInvalidCollection_usesFallback()` | Handle AI returning non-existent collection |
| `selectCollection_fallbackDoesNotExist_createsIt()` | Auto-create fallback collection |
| `selectCollection_circuitBreakerOpen_usesFallback()` | Resilience: fallback when Claude unavailable |
| `selectCollection_circuitBreakerOpen_doesNotCacheFallback()` | Don't cache transient failures |
| `getUserCollections_cacheHit_doesNotCallApi()` | Collections list caching works |
| `getUserCollections_cacheMiss_callsApiAndCaches()` | Collections list fetching and caching |
| `createFallbackCollection_success_invalidatesCollectionsCache()` | Cache invalidation after creation |

**Mock Strategy:**
- Mock `RaindropService`, `YouTubeService`, `ChatClient`
- Use `@Cacheable` test utilities to verify cache behavior
- Capture and verify log messages for warnings/errors

### Integration Tests

**VideoTaggingOrchestratorIT.java:**

| Test Method | Purpose |
|-------------|---------|
| `processPlaylist_aiSelectsCollection_usesCorrectCollection()` | Full end-to-end integration with real Redis |
| `processPlaylist_samePlaylistTwice_usesCachedCollection()` | Verify caching reduces AI calls |
| `processPlaylist_emptyPlaylist_usesFallback()` | Edge case handling |
| `processPlaylist_aiUnavailable_usesFallbackWithoutCaching()` | Circuit breaker integration |

**Setup:**
- Use Testcontainers for Redis
- Mock external APIs (YouTube, Raindrop, Claude) with WireMock or similar
- Verify actual cache entries in Redis

### Controller Tests

**PlaylistControllerTest.java:**

**Updates Required:**
- Remove `raindropCollectionTitle` from all test request bodies
- Verify API accepts requests without collection field
- Verify API rejects requests with unexpected fields (strict parsing)

**Example:**
```java
@Test
void tagPlaylist_withoutCollectionField_returns200() {
    String requestBody = """
        {
            "playlistInput": "PLxxx...",
            "filters": {},
            "tagStrategy": "SUGGEST"
        }
        """;
    // Assert 200 OK and SSE stream
}
```

### Scheduler Tests

**PlaylistProcessingSchedulerTest.java:**

**Updates Required:**
- Verify scheduler no longer uses `DEFAULT_COLLECTION` constant
- Mock `VideoTaggingOrchestrator` to verify it's called without collection
- Verify multiple playlists each get independent AI analysis (no shared collection)

**Example:**
```java
@Test
void processTagPlaylist_multiplePlaylists_eachGetsIndependentAnalysis() {
    // Verify orchestrator.processPlaylist() called once per playlist
    // Verify no collection parameter passed
}
```

## Migration Impact

### Breaking Changes

**Impact:** BREAKING - API contract changes

**What's Breaking:**
1. `TagPlaylistRequest.raindropCollectionTitle` field removed
2. API clients must update to remove this field from requests
3. No backwards compatibility - clean break for simpler design

### Migration Path

**For API Users:**

**Before (old API call):**
```json
POST /api/v1/playlists/tag
{
    "playlistInput": "PLxxx...",
    "raindropCollectionTitle": "Technology",
    "tagStrategy": "SUGGEST"
}
```

**After (new API call):**
```json
POST /api/v1/playlists/tag
{
    "playlistInput": "PLxxx...",
    "tagStrategy": "SUGGEST"
}
```

**For Scheduler Users:**

No code changes required.

**Configuration changes (optional):**
```yaml
vidtag:
  raindrop:
    fallback-collection: "Videos"  # Optional - customize if "Videos" isn't suitable
    collection-cache-ttl: "24h"    # Optional - tune based on needs
    collections-list-cache-ttl: "1h"  # Optional - tune based on needs
```

**Environment variables (optional):**
```bash
VIDTAG_RAINDROP_FALLBACK_COLLECTION="Uncategorized"
VIDTAG_RAINDROP_COLLECTION_CACHE_TTL="48h"
VIDTAG_RAINDROP_COLLECTIONS_LIST_CACHE_TTL="2h"
```

### Deprecation Strategy

**Decision:** No deprecation period - immediate removal

**Rationale:**
- Early-stage project (minimal external users)
- Clean break is simpler than maintaining dual code paths
- Clear error messages guide migration

## Documentation Updates

### CLAUDE.md Changes

**Remove/Update:**
- Remove references to specifying collection in API calls
- Remove `DEFAULT_COLLECTION` from scheduler documentation
- Update API endpoint example (remove collection field)

**Add New Section:**

```markdown
### AI-Powered Collection Selection

VidTag automatically determines the best Raindrop.io collection for each YouTube playlist:

**How It Works:**
1. Analyzes playlist metadata (title, description)
2. Samples first 5-10 video titles from the playlist
3. Uses Claude AI to choose the most appropriate collection from your existing Raindrop collections
4. Falls back to configured collection when uncertain
5. Caches decisions to reduce API calls (configurable TTL)

**Configuration:**
```yaml
vidtag:
  raindrop:
    fallback-collection: "Videos"  # Used when AI confidence is low (default: "Videos")
    collection-cache-ttl: "24h"    # How long to cache playlist → collection mappings (default: 24h)
    collections-list-cache-ttl: "1h"  # How long to cache your collections list (default: 1h)
```

**Fallback Triggers:**
- AI explicitly indicates low confidence
- AI suggests a collection that doesn't exist
- Circuit breaker open (Claude API unavailable)
- Empty playlist (no videos to analyze)
- YouTube API failure (can't fetch playlist metadata)

**When fallback collection doesn't exist:**
VidTag automatically creates it to prevent processing failures.
```

**Update API Documentation:**

```markdown
## API Endpoint

### Tag Playlist

```
POST /api/v1/playlists/tag
Content-Type: application/json
Accept: text/event-stream
```

**Request Body:**
```json
{
  "playlistInput": "PLxxx... or https://www.youtube.com/playlist?list=PLxxx...",
  "filters": {
    "maxVideos": 50,
    "skipExisting": true,
    "minDuration": "PT5M"
  },
  "tagStrategy": "SUGGEST",
  "verbosity": "NORMAL"
}
```

**Notes:**
- Collection is automatically determined by AI (not specified in request)
- All fields except `playlistInput` are optional
```

### Breaking Change Notice

Add to release notes / changelog:

```markdown
## Breaking Changes in v2.0

### AI-Powered Collection Selection

**BREAKING:** Removed `raindropCollectionTitle` field from API

VidTag now automatically determines the appropriate Raindrop collection using AI analysis.
The collection field has been removed from the API request.

**Migration:**
- **API users:** Remove `raindropCollectionTitle` from request bodies
- **Scheduler users:** No changes required (previously used hardcoded "Videos" collection)

**Configuration (optional):**
Customize the fallback collection via:
```yaml
vidtag:
  raindrop:
    fallback-collection: "Videos"
```

**Benefits:**
- No manual collection selection required
- Intelligent categorization based on playlist content
- Consistent behavior across API and scheduler
- Cached decisions reduce AI API costs
```

## Implementation Checklist

### Core Implementation

- [ ] Create `RaindropProperties.java` configuration class
- [ ] Update `application.yaml` with new properties
- [ ] Implement `CollectionSelectionService.java`
  - [ ] `selectCollection(playlistId)` method with caching
  - [ ] `getUserCollections()` method with caching
  - [ ] `constructAIPrompt()` for Claude API
  - [ ] `parseAIResponse()` with validation
  - [ ] Fallback logic and auto-creation
  - [ ] Cache invalidation after collection creation
- [ ] Add new cache configurations to `CacheConfig.java`
- [ ] Update `TagPlaylistRequest.java` - remove `raindropCollectionTitle`
- [ ] Update `VideoTaggingOrchestrator.java`
  - [ ] Inject `CollectionSelectionService`
  - [ ] Call `selectCollection()` in `processPlaylist()`
  - [ ] Pass selected collection to existing flow
- [ ] Update `PlaylistProcessingScheduler.java`
  - [ ] Remove `DEFAULT_COLLECTION` constant
  - [ ] Remove collection from request construction
- [ ] Add `RaindropService.createCollection()` method
- [ ] Add `RaindropService.getUserCollections()` method (cacheable)
- [ ] Add circuit breaker for collection selection

### Testing

- [ ] Unit tests for `CollectionSelectionService` (11 test cases)
- [ ] Integration tests for `VideoTaggingOrchestrator` (4 test cases)
- [ ] Update `PlaylistControllerTest` (remove collection field)
- [ ] Update `PlaylistProcessingSchedulerTest` (verify no hardcoded collection)
- [ ] Test cache invalidation after fallback creation
- [ ] Test all error scenarios (6 scenarios documented)

### Documentation

- [ ] Update CLAUDE.md
  - [ ] Add "AI-Powered Collection Selection" section
  - [ ] Update API endpoint documentation
  - [ ] Update scheduler documentation
  - [ ] Add configuration examples
  - [ ] Add breaking change notice
- [ ] Update README.md (if applicable)
- [ ] Add migration guide to release notes

### Validation

- [ ] All tests pass
- [ ] Manual testing with multiple playlists
- [ ] Verify cache entries in Redis
- [ ] Verify fallback creation works
- [ ] Verify circuit breaker behavior
- [ ] Load testing (verify cache reduces AI calls as expected)

## Non-Goals

**Explicitly NOT included in this design:**

- Per-video collection selection (rejected for complexity/cost)
- Manual collection override in API (rejected for simplicity)
- Backwards compatibility support (clean break preferred)
- Creating new collections based on AI suggestions (only choose from existing)
- Parallel processing of collection selection (sequential is fine)
- Collection hierarchy or nested collections (Raindrop limitation)
- Multi-language support for AI prompts (English only for now)

## Success Criteria

**Feature is successful when:**

1. ✅ API accepts requests without collection field
2. ✅ Scheduler processes playlists without hardcoded collection
3. ✅ AI successfully chooses appropriate collections for diverse playlists
4. ✅ Fallback collection used when AI uncertain
5. ✅ Caching reduces AI API calls (verify same playlist uses cache on 2nd run)
6. ✅ Collections list caching reduces Raindrop API calls
7. ✅ Fallback auto-created when missing
8. ✅ All error scenarios handled gracefully
9. ✅ All tests pass (unit + integration)
10. ✅ Documentation updated and clear

## Future Enhancements

**Not in scope for initial release, but potential future work:**

1. **Per-video collection selection** - Allow videos within a playlist to go to different collections
2. **Collection confidence scores** - Log AI confidence levels for monitoring
3. **Collection suggestion API** - Endpoint to preview AI's collection choice without processing
4. **Multi-collection support** - Save to multiple collections (requires Raindrop API support)
5. **Collection templates** - Define rules/patterns for common playlist types
6. **Analytics dashboard** - Track collection distribution, AI accuracy, cache hit rates
7. **Manual override mechanism** - Optional user review/approval workflow
