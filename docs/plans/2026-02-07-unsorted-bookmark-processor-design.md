# Unsorted Bookmark Processor Design

**Date:** 2026-02-07
**Status:** Proposed

## Overview

Add a scheduled processor that monitors the Raindrop.io "Unsorted" collection for new YouTube videos, generates tags using the existing AI tagging pipeline, determines the appropriate Raindrop collection using per-video AI analysis, and moves each bookmark from Unsorted to the target collection with tags applied.

## Requirements

- Run on a configurable schedule (similar to `PlaylistProcessingScheduler`)
- Fetch bookmarks from Raindrop's Unsorted collection (collection ID `-1`)
- Filter to YouTube URLs only (`youtube.com` / `youtu.be`)
- For each YouTube bookmark: fetch video metadata, generate tags, select collection, move bookmark
- Update bookmarks in place (preserves creation date, notes, etc.)
- Per-video AI call for collection selection (no playlist context available)
- Individual video failures don't stop processing of remaining videos
- Disabled by default

## Design Decisions

### 1. YouTube URLs Only

**Decision:** Only process bookmarks with YouTube URLs.

**Rationale:** The existing tagging pipeline (`VideoTaggingService`, `VideoMetadata`) is built around YouTube video metadata. Supporting arbitrary URLs would require a generic metadata extraction approach, which is out of scope.

### 2. Per-Video Collection Selection

**Decision:** One AI call per video to determine the target collection.

**Rationale:** Unlike the playlist processor, unsorted bookmarks have no playlist context (title, description, related videos). Each video must be evaluated individually based on its own title and description.

### 3. Update Bookmarks In Place

**Decision:** Use `PUT /raindrop/{id}` to update collection and tags on the existing bookmark.

**Rationale:** Preserves the original bookmark metadata (creation date, notes, highlights). Delete-and-recreate would lose user edits.

### 4. No SSE / No Batching

**Decision:** Simple sequential processing with logging only.

**Rationale:** This is a background scheduled job with no interactive client. The playlist orchestrator's SSE streaming and batch partitioning add unnecessary complexity here.

## Changes Required

### New Files

#### 1. `UnsortedProcessorProperties.java`

Configuration properties class under `vidtag.unsorted-processor`:
- `enabled` (boolean, default: `false`)
- `fixedDelay` (Duration, default: `1h`)
- `initialDelay` (Duration, default: `30s`)

Follows the same pattern as `SchedulerProperties`.

#### 2. `UnsortedBookmarkProcessor.java`

Scheduled service (mirrors `PlaylistProcessingScheduler` structure):
- `@ConditionalOnProperty(prefix = "vidtag.unsorted-processor", name = "enabled", havingValue = "true")`
- `@Scheduled` method with configurable fixed delay and initial delay
- Dependencies: `RaindropService`, `YouTubeService`, `VideoTaggingService`, `CollectionSelectionService`, `UnsortedProcessorProperties`

**Processing flow:**
1. Call `raindropService.getUnsortedRaindrops()` to list bookmarks in Unsorted
2. Filter to YouTube URLs (check for `youtube.com` or `youtu.be` in the link)
3. For each matching bookmark:
   a. Extract YouTube video ID from the bookmark URL
   b. Fetch `VideoMetadata` from YouTube API via `youtubeService.getVideoMetadata(videoId)`
   c. Fetch existing Raindrop tags via `raindropService.getUserTags(userId)` (cached)
   d. Generate tags via `videoTaggingService.generateTags(video, existingTags, TagStrategy.SUGGEST)`
   e. Select collection via `collectionSelectionService.selectCollectionForVideo(video)`
   f. Resolve collection ID via `raindropService.resolveCollectionId(userId, collectionTitle)`
   g. Update bookmark via `raindropService.updateRaindrop(raindropId, collectionId, tagNames)`
4. Log summary (total, succeeded, failed)

#### 3. `Raindrop.java` (model)

New model record representing a bookmark returned from the Raindrop API:
```java
public record Raindrop(Long id, String link, String title) {}
```

Needed because the current `RaindropItem` is a private DTO inside `RaindropApiClientImpl`.

### Modified Files

#### 4. `RaindropApiClient.java` — 2 new methods

```java
List<Raindrop> getRaindrops(Long collectionId);
void updateRaindrop(Long raindropId, Long collectionId, List<String> tags);
```

- `getRaindrops`: Calls `GET /raindrops/{collectionId}` with pagination (50 per page). Returns all bookmarks in the collection.
- `updateRaindrop`: Calls `PUT /raindrop/{raindropId}` with body `{ "collection": { "$id": collectionId }, "tags": [...] }`.

#### 5. `RaindropApiClientImpl.java` — implement 2 new methods

- `getRaindrops`: Paginated fetch using `perpage=50` and `page` parameter. Maps response items to `Raindrop` model.
- `updateRaindrop`: PUT request with collection and tags in the body.

#### 6. `RaindropService.java` — 2 new methods

```java
List<Raindrop> getUnsortedRaindrops();
void updateRaindrop(Long raindropId, Long collectionId, List<String> tags);
```

- `getUnsortedRaindrops`: Calls `raindropApiClient.getRaindrops(-1L)`. Protected by circuit breaker.
- `updateRaindrop`: Delegates to `raindropApiClient.updateRaindrop()`. Protected by circuit breaker.

#### 7. `YouTubeApiClient.java` — 1 new method

```java
VideoMetadata getVideo(String videoId);
```

Fetches a single video's metadata (title, description, published date, duration) using `GET /videos?id={videoId}&part=snippet,contentDetails`.

#### 8. `YouTubeApiClientImpl.java` — implement `getVideo`

Uses the existing YouTube service's `videos().list()` API (already used internally by `getVideoDuration`). Builds a full `VideoMetadata` from the response.

#### 9. `YouTubeService.java` — 1 new method

```java
VideoMetadata getVideoMetadata(String videoId);
```

Wraps `youtubeApiClient.getVideo(videoId)` with circuit breaker and retry, following the same pattern as existing methods.

Also needs a utility method to extract a video ID from a YouTube URL:
```java
String extractVideoId(String url);
```

Parses `youtube.com/watch?v=XXX` and `youtu.be/XXX` formats.

#### 10. `CollectionSelectionService.java` — 1 new method

```java
String selectCollectionForVideo(VideoMetadata video);
```

- Fetches available collections from `raindropService.getUserCollections()`
- Builds a prompt with the video's title and description plus the available collections
- Asks AI to choose the best collection (same rules: exact match, LOW_CONFIDENCE fallback)
- Returns the validated collection title
- No caching (each video is unique)

#### 11. `application.yaml` — new config block

```yaml
vidtag:
  unsorted-processor:
    enabled: false
    fixed-delay: 1h
    initial-delay: 30s
```

#### 12. Test Stubs

Add stub implementations for the new `RaindropApiClient` and `YouTubeApiClient` methods in `TestcontainersConfiguration`:
- `getRaindrops()` returns empty list
- `updateRaindrop()` no-op
- `getVideo()` returns null

## Configuration

### Properties

| Property | Default | Description |
|---|---|---|
| `vidtag.unsorted-processor.enabled` | `false` | Enable/disable the unsorted bookmark processor |
| `vidtag.unsorted-processor.fixed-delay` | `1h` | Delay between runs |
| `vidtag.unsorted-processor.initial-delay` | `30s` | Delay before first run |

### Environment Variables

```bash
VIDTAG_UNSORTED_PROCESSOR_ENABLED=true
VIDTAG_UNSORTED_PROCESSOR_FIXED_DELAY=2h
VIDTAG_UNSORTED_PROCESSOR_INITIAL_DELAY=1m
```

## Error Handling

- **Individual video failure**: Log error, increment failed count, continue to next video.
- **YouTube API failure** (video lookup): Skip that video, log warning.
- **AI collection selection failure**: Fall back to configured fallback collection.
- **Raindrop update failure**: Log error, count as failed.
- **Circuit breaker open**: Entire run fails fast with logged error (same as playlist scheduler).

## Future Considerations

- Support non-YouTube URLs with generic metadata extraction
- Configurable source collection (not just Unsorted)
- Batch AI collection selection for efficiency
