# Processed Videos Endpoint — Design

**Date:** 2026-05-11
**Status:** Approved (pre-implementation)

## Summary

Add `GET /processed`, a browser-friendly HTML page that lists every video the
app has processed across both pipelines (playlist tagging and unsorted bookmark
sweep). Backed by a single Redis `LIST`, capped at 100 entries, newest-first.
Producers publish a Spring `ApplicationEvent` after each processed video; an
`@Async @EventListener` records the entry. The controller reads the list and
renders a small HTML table.

## Goals

- Provide a glanceable, browser-renderable view of recent processing activity.
- Work for both the playlist scheduler/orchestrator and the unsorted bookmark
  processor without coupling the producers to the recorder.
- Survive without an external persistence layer — Redis is sufficient.
- Add no new runtime dependencies.

## Non-Goals

- Per-entry retention guarantees across long downtime (Redis is the only store;
  if Redis is wiped, the list is gone).
- Pagination, filtering, search, or sorting — the list is a fixed 100-entry
  window, newest-first.
- Authentication — the app is not publicly exposed.
- Live updates (SSE / websocket) — manual browser refresh only.

## Decisions (with reasoning)

| Decision | Choice | Reasoning |
| --- | --- | --- |
| Presentation | Server-rendered HTML table, manual refresh | Matches "simple list"; no JS, no client tooling. |
| Scope | Both pipelines, distinguished by a `source` column | Most literal reading of "every processed video". |
| Columns | Timestamp, Source, Title (linked to YouTube), Status, Tags, Collection | "Standard" set — enough to verify tagging is doing what's expected without visual noise. |
| Lifecycle | Survives app restart; bounded to last 100 entries | Simpler than clear-on-startup; user accepted the tradeoff that entries from a previous boot may appear. |
| URL path | `GET /processed` (top-level) | Friendly to type in a browser; doesn't pretend to be part of the JSON `/api/v1/*` namespace. |
| Producer → recorder coupling | Spring `ApplicationEvent` + `@Async @EventListener` | Producers don't depend on the recorder type; adding a third pipeline later requires no recorder change. `@EnableAsync` is already active on `VidtagApplication`. |
| Redis structure | `LIST` with `LPUSH` + `LTRIM 0 99` | O(1) push, O(1) cap-trim, naturally newest-first iteration. No need for score-based queries. |
| Templating | Plain Java text blocks in the controller | One small page; pulling in Thymeleaf would be more ceremony than rendering. |

## Architecture

```
[VideoTaggingOrchestrator]                       [UnsortedBookmarkProcessor]
        |                                                          |
        | builds ProcessedVideoEntry                                | builds ProcessedVideoEntry
        | publishEvent(VideoProcessedEvent)                         | publishEvent(VideoProcessedEvent)
        |                                                          |
        +-------------------------+-------------------------+------+
                                  v
                Spring ApplicationEventMulticaster
                                  v
                 [async — SimpleAsyncTaskExecutor]
                                  v
        ProcessedVideoRecorder.onVideoProcessed()
                                  |  LPUSH vidtag:processed:recent <json>
                                  |  LTRIM vidtag:processed:recent 0 99
                                  v
                            (Redis LIST)
                                  ^
                                  |  LRANGE 0 -1
                                  |
                  ProcessedVideoRecorder.recent()
                                  ^
                                  |
                  ProcessedVideosController.list()  ── HTML ──> Browser
```

The producer constructs the entry (including `Instant.now()`), so the entry's
timestamp reflects when the video actually finished processing — not when the
async listener happened to run. The listener's only job is to serialize and
write to Redis.

## Data Model

### `model/ProcessedVideoEntry.java` (new)

```java
public record ProcessedVideoEntry(
    Instant timestamp,
    Source source,
    String title,
    String url,
    ProcessingStatus status,   // reuses existing enum: SUCCESS | SKIPPED | FAILED
    List<String> tags,         // flattened tag labels (no confidence scores)
    String collection          // Raindrop collection name; null if not applicable
) {}
```

### `model/Source.java` (new)

```java
public enum Source { PLAYLIST, UNSORTED }
```

### `event/VideoProcessedEvent.java` (new)

```java
public record VideoProcessedEvent(ProcessedVideoEntry entry) {}
```

Published via `ApplicationEventPublisher.publishEvent(Object)` — Spring 6+
supports plain POJO/record events; no need to extend `ApplicationEvent`.

## Redis Schema

- **Key:** `vidtag:processed:recent` (single key, app-wide)
- **Type:** `LIST`
- **Element:** JSON-serialized `ProcessedVideoEntry` (Jackson, using the
  existing `JacksonConfiguration` `ObjectMapper`; `JavaTimeModule` already
  registered there serializes `Instant` as ISO-8601).
- **Write:** `LPUSH` followed by `LTRIM key 0 99`. Two-call sequence; not
  atomic, but the worst case is a momentary 101-element list which the very
  next write trims back. Acceptable for this volume.
- **Read:** `LRANGE key 0 -1` — newest-first, no client-side sort.

## Components

### New files

| Path | Role |
| --- | --- |
| `src/main/java/org/bartram/vidtag/model/ProcessedVideoEntry.java` | record |
| `src/main/java/org/bartram/vidtag/model/Source.java` | enum |
| `src/main/java/org/bartram/vidtag/event/VideoProcessedEvent.java` | application event |
| `src/main/java/org/bartram/vidtag/service/ProcessedVideoRecorder.java` | writer (`@Async @EventListener`) + reader |
| `src/main/java/org/bartram/vidtag/controller/ProcessedVideosController.java` | `GET /processed` → HTML |

### Modified files

| Path | Change |
| --- | --- |
| `src/main/java/org/bartram/vidtag/service/VideoTaggingOrchestrator.java` | Inject `ApplicationEventPublisher`. Publish `VideoProcessedEvent` at each of the three existing `new VideoProcessingResult(...)` sites (SKIPPED / SUCCESS / FAILED). The chosen `collectionName` is already in scope at the per-video loop. |
| `src/main/java/org/bartram/vidtag/service/UnsortedBookmarkProcessor.java` | Inject `ApplicationEventPublisher`. Publish event from the success branch, the skip branch, and the catch block inside `processRaindrop(...)`. `processRaindrop` keeps its `boolean` return — publishing is a side effect alongside the existing Raindrop write. |

### `ProcessedVideoRecorder` surface

```java
@Component
@Slf4j
@RequiredArgsConstructor
public class ProcessedVideoRecorder {
    private static final String KEY = "vidtag:processed:recent";
    private static final long MAX_INDEX = 99L;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Async
    @EventListener
    public void onVideoProcessed(VideoProcessedEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event.entry());
            redis.opsForList().leftPush(KEY, json);
            redis.opsForList().trim(KEY, 0, MAX_INDEX);
        } catch (Exception e) {
            log.atWarn()
               .setMessage("Failed to record processed video")
               .addArgument(event.entry().url())
               .setCause(e)
               .log();
        }
    }

    public List<ProcessedVideoEntry> recent() {
        try {
            List<String> raw = redis.opsForList().range(KEY, 0, -1);
            if (raw == null) return List.of();
            return raw.stream()
                      .map(this::deserialize)
                      .filter(Objects::nonNull)
                      .toList();
        } catch (Exception e) {
            log.atWarn().setMessage("Failed to read processed videos list").setCause(e).log();
            return List.of();
        }
    }

    private ProcessedVideoEntry deserialize(String json) {
        try {
            return objectMapper.readValue(json, ProcessedVideoEntry.class);
        } catch (Exception e) {
            log.atWarn().setMessage("Skipping malformed processed video entry").setCause(e).log();
            return null;
        }
    }
}
```

`@EnableAsync` is already active on `VidtagApplication`. The default Spring
async executor (`SimpleAsyncTaskExecutor`) is acceptable at this volume
(roughly one event per processed video). If a pool is wanted later, add a
`TaskExecutor` bean — no API change required.

`@TransactionalEventListener` is **not** used: the producers are not
`@Transactional`, so plain `@EventListener` is correct.

## HTML Rendering

The controller renders the page with Java text blocks. One `<table>`,
columns: `When | Source | Title | Status | Tags | Collection`. The Title cell
renders as `<a href="{url}">{title}</a>` when the URL passes the allowlist
check below, otherwise as plain text. A minimal inline `<style>` block sets a
monospace font and full-width table. No JavaScript.

**Cell formatting:**

- `When`: ISO-8601 instant rendered in UTC (e.g., `2026-05-11T20:14:33Z`).
  Use `DateTimeFormatter.ISO_INSTANT`.
- `Source`: lowercase of the enum name (`playlist` / `unsorted`).
- `Status`: lowercase of the enum name (`success` / `skipped` / `failed`).
- `Tags`: comma-separated, in insertion order, e.g., `golf, swing, drills`.
  Each tag escaped individually before joining.
- `Collection`: literal collection name, or the string `—` (em-dash) when null.
- Empty list: a single `<p>No videos processed yet.</p>` instead of the table.

### Security — XSS and URL safety

Titles, tags, and collection names flow in from YouTube and Raindrop, both
external. All dynamic values are escaped before being inserted into HTML.

- **HTML escaping:** every dynamic value passes through
  `org.springframework.web.util.HtmlUtils.htmlEscape(...)` before being placed
  in the response. Applies to `title`, each tag, `collection`, `status.name()`,
  the formatted `timestamp`, and `source.name()`. `HtmlUtils` is the canonical
  secure-default sanitizer in the Spring stack and is already on the classpath.
- **URL scheme + host allowlist:** before emitting `<a href="...">`, the URL is
  parsed via `URI.create(...)`. The anchor is rendered only when
  `scheme == "https"` **and** the host is one of
  `www.youtube.com`, `youtu.be`, `m.youtube.com`. Otherwise the title is
  rendered as plain text. Prevents `javascript:` or other unexpected schemes
  from reaching the browser if a future change ever wrote a non-YouTube URL.
- **Content-Type** is set explicitly to `text/html; charset=utf-8`.
- **No JS in the page** — eliminates an entire class of issues by construction.

CSRF is not relevant: the only endpoint added is a `GET` with no state change.

## Error Handling

| Path | Failure | Behavior |
| --- | --- | --- |
| Write listener | Redis down, serialization error | try/catch in `onVideoProcessed`, log at WARN, swallow. Recording must never fail a pipeline. |
| Read | Redis down (`LRANGE` throws) | try/catch in `recent()`, return `List.of()`. Controller renders a one-line banner: *"Recent list unavailable — Redis read failed"*. |
| Read | Malformed JSON for a single element | `deserialize(...)` returns null; the element is filtered out, others are rendered. WARN log. |
| Async | Listener exception escapes | Already swallowed by the in-method try/catch (defense-in-depth). |

No Resilience4J wrapping. This is a debug view, not a critical path. The
producers' existing circuit breakers (YouTube/Raindrop/Claude) are unaffected
because `publishEvent(...)` only enqueues — the Redis I/O happens on the async
thread.

## Testing

Three test classes, all using the existing Testcontainers Redis setup
(`TestcontainersConfiguration`). No new test dependencies.

### 1. `ProcessedVideoRecorderTest` — `@SpringBootTest` with Testcontainers Redis

- `record_thenRecent_returnsEntry`
- `record101Entries_recentReturns100NewestFirstOldestDropped` — verifies the
  `LTRIM 0 99` cap and ordering.
- `recent_onMissingKey_returnsEmptyList`
- `recent_withMalformedJsonElement_skipsThatElementOnly` — manually `LPUSH`es a
  broken string, then a valid entry; asserts the valid entry is returned and
  the broken one is silently dropped.

### 2. `ProcessedVideosControllerTest` — `@WebMvcTest(ProcessedVideosController.class)`, recorder mocked

- Response `Content-Type` is `text/html; charset=utf-8`.
- A title containing `<script>alert(1)</script>` is escaped (no live tag in
  output; assert on `&lt;script&gt;`).
- A non-YouTube URL (`https://evil.example.com/x`) → title renders without an
  `<a>` wrapper.
- A `javascript:alert(1)` URL → title renders without an `<a>` wrapper.
- Empty list → renders a "no entries" placeholder.

### 3. `VideoProcessedEventIT` — `@SpringBootTest` with Testcontainers Redis

- Inject `ApplicationEventPublisher`; publish a `VideoProcessedEvent`.
- Poll loop (`Thread.sleep` + max 2 s wait) until `recorder.recent()` is
  non-empty — verifies the full producer → async listener → Redis flow.
- Awaitility is not on the classpath; a small inline poll loop keeps the spec
  free of new dependencies.

## Build / Deploy Impact

- No new Gradle dependencies.
- No new environment variables or configuration properties.
- Helm chart unchanged. `appVersion` bumps via the normal `./gradlew release`
  flow.
- No database migrations.
- Hot-deployable: rolling restart picks up the new endpoint.

## Open Questions

None at design time. Any surprises during implementation should be raised as
spec amendments rather than silent deviations.
