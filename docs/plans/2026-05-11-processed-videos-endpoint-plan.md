# Processed Videos Endpoint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Spec:** [`docs/plans/2026-05-11-processed-videos-endpoint-design.md`](2026-05-11-processed-videos-endpoint-design.md)

**Goal:** Add `GET /processed`, a browser-rendered HTML page listing the last 100 videos processed by either pipeline, backed by Redis and fed via Spring `ApplicationEvent` + async listener.

**Architecture:** Producers (`VideoTaggingOrchestrator`, `UnsortedBookmarkProcessor`) publish `VideoProcessedEvent` after each video. An `@Async @EventListener` on `ProcessedVideoRecorder` serializes to JSON and writes to a Redis `LIST` with `LPUSH + LTRIM 0 99`. A new controller reads the list and renders a small HTML table with full XSS escaping and a URL allowlist for the title links.

**Tech Stack:** Spring Boot 4.0.6, Java 25, Spring Data Redis (`StringRedisTemplate`), Jackson (`ObjectMapper` already configured), JUnit 5, Mockito, Testcontainers Redis (already in `TestcontainersConfiguration`).

---

## File Map

**Created:**
- `src/main/java/org/bartram/vidtag/model/Source.java`
- `src/main/java/org/bartram/vidtag/model/ProcessedVideoEntry.java`
- `src/main/java/org/bartram/vidtag/event/VideoProcessedEvent.java`
- `src/main/java/org/bartram/vidtag/service/ProcessedVideoRecorder.java`
- `src/main/java/org/bartram/vidtag/controller/ProcessedVideosController.java`
- `src/test/java/org/bartram/vidtag/model/ProcessedVideoEntryTest.java`
- `src/test/java/org/bartram/vidtag/service/ProcessedVideoRecorderTest.java`
- `src/test/java/org/bartram/vidtag/controller/ProcessedVideosControllerTest.java`
- `src/test/java/org/bartram/vidtag/service/VideoProcessedEventIT.java`

**Modified:**
- `src/main/java/org/bartram/vidtag/service/VideoTaggingOrchestrator.java`
- `src/main/java/org/bartram/vidtag/service/UnsortedBookmarkProcessor.java`

`@EnableAsync` is already on `VidtagApplication.java`, and `event/package-info.java` already exists — no config or package scaffolding required.

---

## Task 1: Core types (model + event)

**Files:**
- Create: `src/main/java/org/bartram/vidtag/model/Source.java`
- Create: `src/main/java/org/bartram/vidtag/model/ProcessedVideoEntry.java`
- Create: `src/main/java/org/bartram/vidtag/event/VideoProcessedEvent.java`
- Test: `src/test/java/org/bartram/vidtag/model/ProcessedVideoEntryTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/org/bartram/vidtag/model/ProcessedVideoEntryTest.java`:

```java
package org.bartram.vidtag.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcessedVideoEntryTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void jsonRoundtrip_preservesAllFields() throws Exception {
        ProcessedVideoEntry entry = new ProcessedVideoEntry(
                Instant.parse("2026-05-11T20:14:33Z"),
                Source.PLAYLIST,
                "How to swing a golf club",
                "https://www.youtube.com/watch?v=abc123",
                ProcessingStatus.SUCCESS,
                List.of("golf", "swing"),
                "Golf Tutorials");

        String json = mapper.writeValueAsString(entry);
        ProcessedVideoEntry roundtripped = mapper.readValue(json, ProcessedVideoEntry.class);

        assertThat(roundtripped).isEqualTo(entry);
    }

    @Test
    void jsonRoundtrip_handlesNullCollection() throws Exception {
        ProcessedVideoEntry entry = new ProcessedVideoEntry(
                Instant.parse("2026-05-11T20:14:33Z"),
                Source.UNSORTED,
                "x",
                "https://youtu.be/x",
                ProcessingStatus.SKIPPED,
                List.of(),
                null);

        String json = mapper.writeValueAsString(entry);
        ProcessedVideoEntry roundtripped = mapper.readValue(json, ProcessedVideoEntry.class);

        assertThat(roundtripped.collection()).isNull();
        assertThat(roundtripped).isEqualTo(entry);
    }

    @Test
    void timestampSerializesAsIso8601String() throws Exception {
        ProcessedVideoEntry entry = new ProcessedVideoEntry(
                Instant.parse("2026-05-11T20:14:33Z"),
                Source.PLAYLIST,
                "x",
                "https://www.youtube.com/watch?v=x",
                ProcessingStatus.SUCCESS,
                List.of(),
                "Videos");

        String json = mapper.writeValueAsString(entry);

        assertThat(json).contains("\"2026-05-11T20:14:33Z\"");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'org.bartram.vidtag.model.ProcessedVideoEntryTest' -q`
Expected: FAIL — `ProcessedVideoEntry`, `Source` do not exist.

- [ ] **Step 3: Create `Source.java`**

`src/main/java/org/bartram/vidtag/model/Source.java`:

```java
package org.bartram.vidtag.model;

/**
 * Which pipeline produced a {@link ProcessedVideoEntry}.
 */
public enum Source {
    /** Playlist tagging pipeline (YouTube playlists → Raindrop). */
    PLAYLIST,
    /** Unsorted bookmark processor (Raindrop Unsorted → tagged + moved). */
    UNSORTED
}
```

- [ ] **Step 4: Create `ProcessedVideoEntry.java`**

`src/main/java/org/bartram/vidtag/model/ProcessedVideoEntry.java`:

```java
package org.bartram.vidtag.model;

import java.time.Instant;
import java.util.List;

/**
 * One row in the recent-processed-videos view shown at {@code GET /processed}.
 *
 * @param timestamp  when processing finished
 * @param source     which pipeline produced this entry
 * @param title      video title
 * @param url        YouTube watch URL
 * @param status     processing outcome
 * @param tags       flattened AI-suggested tag labels (no confidence scores)
 * @param collection Raindrop collection name, or null if not applicable
 */
public record ProcessedVideoEntry(
        Instant timestamp,
        Source source,
        String title,
        String url,
        ProcessingStatus status,
        List<String> tags,
        String collection) {}
```

- [ ] **Step 5: Create `VideoProcessedEvent.java`**

`src/main/java/org/bartram/vidtag/event/VideoProcessedEvent.java`:

```java
package org.bartram.vidtag.event;

import org.bartram.vidtag.model.ProcessedVideoEntry;

/**
 * Spring application event published by pipelines after a single video is
 * processed. A POJO record published via
 * {@code ApplicationEventPublisher.publishEvent(Object)} — Spring 6+ supports
 * non-{@code ApplicationEvent} payloads.
 */
public record VideoProcessedEvent(ProcessedVideoEntry entry) {}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew test --tests 'org.bartram.vidtag.model.ProcessedVideoEntryTest' -q`
Expected: PASS (3 tests).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/bartram/vidtag/model/Source.java \
        src/main/java/org/bartram/vidtag/model/ProcessedVideoEntry.java \
        src/main/java/org/bartram/vidtag/event/VideoProcessedEvent.java \
        src/test/java/org/bartram/vidtag/model/ProcessedVideoEntryTest.java
git commit -m "feat(processed): add ProcessedVideoEntry, Source, VideoProcessedEvent types"
```

---

## Task 2: ProcessedVideoRecorder — write path + cap

**Files:**
- Create: `src/main/java/org/bartram/vidtag/service/ProcessedVideoRecorder.java`
- Test: `src/test/java/org/bartram/vidtag/service/ProcessedVideoRecorderTest.java`

The recorder has a package-private synchronous `record(entry)` and a public `recent()`. The `@Async @EventListener` method is a thin wrapper around `record(...)` — this lets tests bypass the async proxy and assert synchronously.

- [ ] **Step 1: Write the failing test**

`src/test/java/org/bartram/vidtag/service/ProcessedVideoRecorderTest.java`:

```java
package org.bartram.vidtag.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.bartram.vidtag.TestcontainersConfiguration;
import org.bartram.vidtag.model.ProcessedVideoEntry;
import org.bartram.vidtag.model.ProcessingStatus;
import org.bartram.vidtag.model.Source;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProcessedVideoRecorderTest {

    private static final String KEY = "vidtag:processed:recent";

    @Autowired
    private ProcessedVideoRecorder recorder;

    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanUp() {
        redis.delete(KEY);
    }

    @Test
    void record_thenRecent_returnsEntry() {
        ProcessedVideoEntry entry = sample("a");

        recorder.record(entry);

        assertThat(recorder.recent()).containsExactly(entry);
    }

    @Test
    void record101Entries_recentReturns100NewestFirstOldestDropped() {
        for (int i = 0; i < 101; i++) {
            recorder.record(sample("v" + i));
        }

        List<ProcessedVideoEntry> recent = recorder.recent();

        assertThat(recent).hasSize(100);
        assertThat(recent.get(0).title()).isEqualTo("v100"); // newest at head
        assertThat(recent.get(99).title()).isEqualTo("v1");  // v0 was trimmed off
    }

    private ProcessedVideoEntry sample(String title) {
        return new ProcessedVideoEntry(
                Instant.parse("2026-05-11T20:14:33Z"),
                Source.PLAYLIST,
                title,
                "https://www.youtube.com/watch?v=" + title,
                ProcessingStatus.SUCCESS,
                List.of("golf"),
                "Videos");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'org.bartram.vidtag.service.ProcessedVideoRecorderTest' -q`
Expected: FAIL — `ProcessedVideoRecorder` does not exist.

- [ ] **Step 3: Create the recorder**

`src/main/java/org/bartram/vidtag/service/ProcessedVideoRecorder.java`:

```java
package org.bartram.vidtag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.vidtag.event.VideoProcessedEvent;
import org.bartram.vidtag.model.ProcessedVideoEntry;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Records processed videos to a capped Redis list and reads them back for the
 * {@code GET /processed} view. Single source of truth for the
 * {@code vidtag:processed:recent} key.
 *
 * <p>Recording failures must not propagate — they are logged and swallowed so
 * that a Redis outage cannot fail a pipeline.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProcessedVideoRecorder {

    static final String KEY = "vidtag:processed:recent";
    static final long MAX_INDEX = 99L; // keep 100 most recent entries

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /**
     * Async listener that records the event's entry. Calls
     * {@link #record(ProcessedVideoEntry)} so that tests can bypass the async
     * proxy by invoking {@code record} directly.
     */
    @Async
    @EventListener
    public void onVideoProcessed(VideoProcessedEvent event) {
        record(event.entry());
    }

    /**
     * Writes one entry to the head of the Redis list and trims to the cap.
     * Package-private to allow synchronous unit tests.
     */
    void record(ProcessedVideoEntry entry) {
        try {
            String json = objectMapper.writeValueAsString(entry);
            redis.opsForList().leftPush(KEY, json);
            redis.opsForList().trim(KEY, 0, MAX_INDEX);
        } catch (Exception e) {
            log.atWarn()
                    .setMessage("Failed to record processed video: {}")
                    .addArgument(entry.url())
                    .setCause(e)
                    .log();
        }
    }

    /**
     * Returns the recorded entries, newest first. Returns an empty list on any
     * read failure (Redis down, malformed JSON, etc.).
     */
    public List<ProcessedVideoEntry> recent() {
        try {
            List<String> raw = redis.opsForList().range(KEY, 0, -1);
            if (raw == null) {
                return List.of();
            }
            return raw.stream().map(this::deserialize).filter(Objects::nonNull).toList();
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

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests 'org.bartram.vidtag.service.ProcessedVideoRecorderTest' -q`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/bartram/vidtag/service/ProcessedVideoRecorder.java \
        src/test/java/org/bartram/vidtag/service/ProcessedVideoRecorderTest.java
git commit -m "feat(processed): add ProcessedVideoRecorder with capped Redis list"
```

---

## Task 3: Recorder read-side error paths

**Files:**
- Modify: `src/test/java/org/bartram/vidtag/service/ProcessedVideoRecorderTest.java`

- [ ] **Step 1: Write the failing test**

Append these two tests to `ProcessedVideoRecorderTest`:

```java
    @Test
    void recent_onMissingKey_returnsEmptyList() {
        // @AfterEach deletes the key; nothing has been written this test
        assertThat(recorder.recent()).isEmpty();
    }

    @Test
    void recent_withMalformedJsonElement_skipsThatElementOnly() {
        // Manually push a junk string, then a valid entry
        redis.opsForList().leftPush(KEY, "{not valid json");
        recorder.record(sample("good"));

        List<ProcessedVideoEntry> recent = recorder.recent();

        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).title()).isEqualTo("good");
    }
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `./gradlew test --tests 'org.bartram.vidtag.service.ProcessedVideoRecorderTest' -q`
Expected: PASS (4 tests total) — the recorder's existing error handling already covers these.

If either test fails, fix the recorder's `recent()` / `deserialize()` methods rather than the test.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/org/bartram/vidtag/service/ProcessedVideoRecorderTest.java
git commit -m "test(processed): cover recorder read-side error paths"
```

---

## Task 4: Wire VideoTaggingOrchestrator to publish events

**Files:**
- Modify: `src/main/java/org/bartram/vidtag/service/VideoTaggingOrchestrator.java`
- Modify: `src/test/java/org/bartram/vidtag/service/VideoTaggingOrchestratorTest.java`

The publish site is in `processPlaylist` immediately after `processVideo(...)` returns — at that point both the `VideoProcessingResult` and the `collectionTitle` (chosen on line 65) are in scope, so `processVideo` itself is untouched.

- [ ] **Step 1: Write the failing test**

Open `src/test/java/org/bartram/vidtag/service/VideoTaggingOrchestratorTest.java`. Add this test method (matching the existing class's Mockito-style fixture — the exact field names for mocks may differ; adapt to the existing setup):

```java
    @Test
    void processPlaylist_publishesVideoProcessedEventForEachProcessedVideo() {
        // Arrange: drive the orchestrator with mocks that produce one SUCCESS video.
        // (Reuse the existing test fixture's mocking of YouTube/Raindrop/AI services
        // for a one-video happy path.)

        // Act
        orchestrator.processPlaylist(sampleRequest(), event -> {});

        // Assert: ApplicationEventPublisher saw at least one VideoProcessedEvent
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());

        List<VideoProcessedEvent> processedEvents = captor.getAllValues().stream()
                .filter(VideoProcessedEvent.class::isInstance)
                .map(VideoProcessedEvent.class::cast)
                .toList();

        assertThat(processedEvents).hasSize(1);
        ProcessedVideoEntry entry = processedEvents.get(0).entry();
        assertThat(entry.source()).isEqualTo(Source.PLAYLIST);
        assertThat(entry.status()).isEqualTo(ProcessingStatus.SUCCESS);
        assertThat(entry.collection()).isEqualTo("Videos"); // collectionTitle from the mock
    }
```

In the test class fields, add:

```java
    @Mock
    private ApplicationEventPublisher eventPublisher;
```

And ensure the existing `@InjectMocks` orchestrator picks up the new mock — Mockito will inject by type into the constructor. If the existing test uses constructor wiring explicitly, add `eventPublisher` to that invocation.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'org.bartram.vidtag.service.VideoTaggingOrchestratorTest.processPlaylist_publishesVideoProcessedEventForEachProcessedVideo' -q`
Expected: FAIL — `VideoTaggingOrchestrator` does not have an `ApplicationEventPublisher` field, and `verify(eventPublisher, atLeastOnce())` will report zero invocations.

- [ ] **Step 3: Add the publisher dependency to the orchestrator**

In `VideoTaggingOrchestrator.java`, add the import and field. The class is `@RequiredArgsConstructor`, so adding a `final` field auto-wires it:

```java
import org.springframework.context.ApplicationEventPublisher;
// ... other imports
import org.bartram.vidtag.event.VideoProcessedEvent;
import org.bartram.vidtag.model.ProcessedVideoEntry;
import org.bartram.vidtag.model.Source;
import java.time.Instant;
```

Add to the field list (alongside the other `private final` dependencies):

```java
    private final ApplicationEventPublisher eventPublisher;
```

- [ ] **Step 4: Publish from `processPlaylist` after each `processVideo` call**

Currently `processPlaylist` looks like:

```java
                for (VideoMetadata video : batch) {
                    VideoProcessingResult result =
                            processVideo(video, collectionId, existingTags, request.tagStrategy(), eventEmitter);

                    switch (result.status()) {
                        case SUCCESS -> { ... }
                        case SKIPPED -> { ... }
                        case FAILED -> { ... }
                    }
                }
```

Add a publish call after the `switch`, before the closing brace of the `for`:

```java
                for (VideoMetadata video : batch) {
                    VideoProcessingResult result =
                            processVideo(video, collectionId, existingTags, request.tagStrategy(), eventEmitter);

                    switch (result.status()) {
                        case SUCCESS -> { succeeded++; batchSucceeded++; }
                        case SKIPPED -> { skipped++; batchSkipped++; }
                        case FAILED -> { failed++; batchFailed++; }
                    }

                    publishProcessedEvent(result, collectionTitle);
                }
```

Add the helper method below `processVideo` (so it has access to nothing it doesn't need):

```java
    private void publishProcessedEvent(VideoProcessingResult result, String collectionTitle) {
        List<String> tagLabels = result.selectedTags() == null
                ? List.of()
                : result.selectedTags().stream().map(TagWithConfidence::tag).toList();

        ProcessedVideoEntry entry = new ProcessedVideoEntry(
                Instant.now(),
                Source.PLAYLIST,
                result.video().title(),
                result.video().url(),
                result.status(),
                tagLabels,
                collectionTitle);

        eventPublisher.publishEvent(new VideoProcessedEvent(entry));
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests 'org.bartram.vidtag.service.VideoTaggingOrchestratorTest' -q`
Expected: PASS (all tests in the class, including the new one).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/bartram/vidtag/service/VideoTaggingOrchestrator.java \
        src/test/java/org/bartram/vidtag/service/VideoTaggingOrchestratorTest.java
git commit -m "feat(processed): publish VideoProcessedEvent from playlist pipeline"
```

---

## Task 5: Wire UnsortedBookmarkProcessor to publish events

**Files:**
- Modify: `src/main/java/org/bartram/vidtag/service/UnsortedBookmarkProcessor.java`
- Test: `src/test/java/org/bartram/vidtag/service/UnsortedBookmarkProcessorTest.java` (create if absent)

The unsorted processor has three outcome paths:

1. **SUCCESS** — line ~168 in `processRaindrop` after `raindropService.updateRaindrop(...)`. Full data available (video, tags, collectionTitle).
2. **SKIPPED — video not on YouTube** — line ~152 area. Only `raindrop` is in scope; no `video`, no AI run yet. Use `raindrop.title()` and `raindrop.link()`.
3. **FAILED** — outer catch at line ~112. Only `raindrop` is in scope.

The non-YouTube skip at line ~135-141 is unreachable in practice (the outer loop filters by `extractVideoId` first), so we do not publish from that branch.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/bartram/vidtag/service/UnsortedBookmarkProcessorTest.java` if absent. Add a minimal Mockito test (adapt fixture style to match other service tests in the project):

```java
package org.bartram.vidtag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.bartram.vidtag.config.UnsortedProcessorProperties;
import org.bartram.vidtag.event.VideoProcessedEvent;
import org.bartram.vidtag.model.ProcessedVideoEntry;
import org.bartram.vidtag.model.ProcessingStatus;
import org.bartram.vidtag.model.Raindrop;
import org.bartram.vidtag.model.Source;
import org.bartram.vidtag.model.TagWithConfidence;
import org.bartram.vidtag.model.VideoMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class UnsortedBookmarkProcessorTest {

    @Mock private RaindropService raindropService;
    @Mock private YouTubeService youtubeService;
    @Mock private VideoTaggingService videoTaggingService;
    @Mock private CollectionSelectionService collectionSelectionService;
    @Mock private UnsortedProcessorProperties properties;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UnsortedBookmarkProcessor processor;

    @Test
    void processUnsortedBookmarks_successPath_publishesSuccessEvent() {
        Raindrop raindrop = new Raindrop(1L, "Test", "https://www.youtube.com/watch?v=abc", List.of(), 0L);
        VideoMetadata video = new VideoMetadata("abc", "Test", "https://www.youtube.com/watch?v=abc",
                "channel", null, null);

        when(properties.isEnabled()).thenReturn(true);
        when(raindropService.getUnsortedRaindrops()).thenReturn(List.of(raindrop));
        when(youtubeService.extractVideoId(raindrop.link())).thenReturn("abc");
        when(raindropService.getUserTags(anyString())).thenReturn(List.of());
        when(youtubeService.getVideoMetadata("abc")).thenReturn(video);
        when(videoTaggingService.generateTags(eq(video), anyList(), any()))
                .thenReturn(List.of(new TagWithConfidence("golf", 0.9)));
        when(collectionSelectionService.selectCollectionForVideo(video)).thenReturn("Videos");
        when(raindropService.resolveCollectionId(anyString(), eq("Videos"))).thenReturn(42L);

        processor.processUnsortedBookmarks();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        List<VideoProcessedEvent> events = captor.getAllValues().stream()
                .filter(VideoProcessedEvent.class::isInstance)
                .map(VideoProcessedEvent.class::cast)
                .toList();

        assertThat(events).hasSize(1);
        ProcessedVideoEntry entry = events.get(0).entry();
        assertThat(entry.source()).isEqualTo(Source.UNSORTED);
        assertThat(entry.status()).isEqualTo(ProcessingStatus.SUCCESS);
        assertThat(entry.collection()).isEqualTo("Videos");
        assertThat(entry.tags()).containsExactly("golf");
    }
}
```

*Adjust the `Raindrop` and `VideoMetadata` constructor argument lists to match the actual record signatures in the codebase if they differ from above.*

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'org.bartram.vidtag.service.UnsortedBookmarkProcessorTest' -q`
Expected: FAIL — `UnsortedBookmarkProcessor` does not yet inject `ApplicationEventPublisher`.

- [ ] **Step 3: Inject the publisher**

In `UnsortedBookmarkProcessor.java`, add to the imports and field list:

```java
import org.springframework.context.ApplicationEventPublisher;
import org.bartram.vidtag.event.VideoProcessedEvent;
import org.bartram.vidtag.model.ProcessedVideoEntry;
import org.bartram.vidtag.model.ProcessingStatus;
import org.bartram.vidtag.model.Source;
import java.time.Instant;
```

```java
    private final ApplicationEventPublisher eventPublisher;
```

- [ ] **Step 4: Publish from the three outcome paths**

**SUCCESS path** — in `processRaindrop`, at the end of the successful path. Replace the existing tail of the method:

```java
        // Update the raindrop: move to collection and add tags
        raindropService.updateRaindrop(raindrop.id(), collectionId, tagNames);

        log.atInfo()
                .setMessage("Processed unsorted bookmark '{}': moved to '{}' with {} tags")
                .addArgument(raindrop.title())
                .addArgument(collectionTitle)
                .addArgument(tagNames.size())
                .log();

        publishEntry(buildEntry(
                video.title(), video.url(),
                ProcessingStatus.SUCCESS, tagNames, collectionTitle));

        return true;
```

**SKIPPED — video not on YouTube** — modify the existing `video == null` branch:

```java
        if (video == null) {
            log.atWarn()
                    .setMessage("Video not found on YouTube, skipping: {}")
                    .addArgument(videoId)
                    .log();
            publishEntry(buildEntry(
                    raindrop.title(), raindrop.link(),
                    ProcessingStatus.SKIPPED, List.of(), null));
            return false;
        }
```

**FAILED — outer catch** — in `processUnsortedBookmarks`, modify the catch:

```java
            } catch (Exception e) {
                log.atError()
                        .setMessage("Failed to process raindrop '{}': {}")
                        .addArgument(raindrop.title())
                        .addArgument(e.getMessage())
                        .setCause(e)
                        .log();
                publishEntry(buildEntry(
                        raindrop.title(), raindrop.link(),
                        ProcessingStatus.FAILED, List.of(), null));
                failed++;
            }
```

Add the two helpers at the bottom of the class:

```java
    private ProcessedVideoEntry buildEntry(
            String title, String url, ProcessingStatus status,
            List<String> tags, String collection) {
        return new ProcessedVideoEntry(
                Instant.now(), Source.UNSORTED, title, url, status, tags, collection);
    }

    private void publishEntry(ProcessedVideoEntry entry) {
        eventPublisher.publishEvent(new VideoProcessedEvent(entry));
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests 'org.bartram.vidtag.service.UnsortedBookmarkProcessorTest' -q`
Expected: PASS.

- [ ] **Step 6: Run the full test suite to verify no regressions**

Run: `./gradlew test -q`
Expected: PASS (all tests).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/bartram/vidtag/service/UnsortedBookmarkProcessor.java \
        src/test/java/org/bartram/vidtag/service/UnsortedBookmarkProcessorTest.java
git commit -m "feat(processed): publish VideoProcessedEvent from unsorted pipeline"
```

---

## Task 6: ProcessedVideosController — HTML with XSS escaping + URL allowlist

**Files:**
- Create: `src/main/java/org/bartram/vidtag/controller/ProcessedVideosController.java`
- Test: `src/test/java/org/bartram/vidtag/controller/ProcessedVideosControllerTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/org/bartram/vidtag/controller/ProcessedVideosControllerTest.java`:

```java
package org.bartram.vidtag.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.Instant;
import java.util.List;
import org.bartram.vidtag.model.ProcessedVideoEntry;
import org.bartram.vidtag.model.ProcessingStatus;
import org.bartram.vidtag.model.Source;
import org.bartram.vidtag.service.ProcessedVideoRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(ProcessedVideosController.class)
class ProcessedVideosControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProcessedVideoRecorder recorder;

    @Test
    void get_returnsHtmlContentType() throws Exception {
        when(recorder.recent()).thenReturn(List.of());

        mockMvc.perform(get("/processed"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
    }

    @Test
    void titleWithScriptTagIsEscaped() throws Exception {
        when(recorder.recent()).thenReturn(List.of(entry(
                "<script>alert(1)</script>",
                "https://www.youtube.com/watch?v=abc")));

        String body = body(mockMvc.perform(get("/processed")).andReturn());

        assertThat(body).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(body).doesNotContain("<script>alert(1)</script>");
    }

    @Test
    void nonYoutubeUrl_titleRendersWithoutAnchor() throws Exception {
        when(recorder.recent()).thenReturn(List.of(entry("clean", "https://evil.example.com/x")));

        String body = body(mockMvc.perform(get("/processed")).andReturn());

        assertThat(body).contains(">clean<");
        assertThat(body).doesNotContain("href=\"https://evil.example.com");
    }

    @Test
    void javascriptUrl_titleRendersWithoutAnchor() throws Exception {
        when(recorder.recent()).thenReturn(List.of(entry("payload", "javascript:alert(1)")));

        String body = body(mockMvc.perform(get("/processed")).andReturn());

        assertThat(body).doesNotContain("javascript:alert(1)");
        assertThat(body).doesNotContain("href=\"javascript");
    }

    @Test
    void validYoutubeUrl_titleWrappedInAnchor() throws Exception {
        when(recorder.recent()).thenReturn(List.of(entry("ok", "https://www.youtube.com/watch?v=abc")));

        String body = body(mockMvc.perform(get("/processed")).andReturn());

        assertThat(body).contains("<a href=\"https://www.youtube.com/watch?v=abc\">ok</a>");
    }

    @Test
    void emptyList_rendersPlaceholder() throws Exception {
        when(recorder.recent()).thenReturn(List.of());

        String body = body(mockMvc.perform(get("/processed")).andReturn());

        assertThat(body).contains("No videos processed yet.");
    }

    @Test
    void tagsRenderedCommaSeparatedAndIndividuallyEscaped() throws Exception {
        when(recorder.recent()).thenReturn(List.of(new ProcessedVideoEntry(
                Instant.parse("2026-05-11T20:14:33Z"),
                Source.PLAYLIST,
                "t",
                "https://www.youtube.com/watch?v=abc",
                ProcessingStatus.SUCCESS,
                List.of("safe", "<x>"),
                "Videos")));

        String body = body(mockMvc.perform(get("/processed")).andReturn());

        assertThat(body).contains("safe, &lt;x&gt;");
    }

    @Test
    void nullCollection_rendersEmDash() throws Exception {
        when(recorder.recent()).thenReturn(List.of(new ProcessedVideoEntry(
                Instant.parse("2026-05-11T20:14:33Z"),
                Source.UNSORTED,
                "t",
                "https://www.youtube.com/watch?v=abc",
                ProcessingStatus.FAILED,
                List.of(),
                null)));

        String body = body(mockMvc.perform(get("/processed")).andReturn());

        assertThat(body).contains("—");
    }

    private ProcessedVideoEntry entry(String title, String url) {
        return new ProcessedVideoEntry(
                Instant.parse("2026-05-11T20:14:33Z"),
                Source.PLAYLIST,
                title,
                url,
                ProcessingStatus.SUCCESS,
                List.of(),
                "Videos");
    }

    private String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }
}
```

Required static imports for the matchers:

```java
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'org.bartram.vidtag.controller.ProcessedVideosControllerTest' -q`
Expected: FAIL — `ProcessedVideosController` does not exist.

- [ ] **Step 3: Implement the controller**

`src/main/java/org/bartram/vidtag/controller/ProcessedVideosController.java`:

```java
package org.bartram.vidtag.controller;

import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.bartram.vidtag.model.ProcessedVideoEntry;
import org.bartram.vidtag.service.ProcessedVideoRecorder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

/**
 * Renders {@code GET /processed} — a server-side HTML table of the last 100
 * processed videos. All dynamic values are HTML-escaped, and the title anchor
 * is only emitted when the entry URL passes a scheme + host allowlist.
 */
@Controller
@RequiredArgsConstructor
public class ProcessedVideosController {

    private static final Set<String> ALLOWED_HOSTS =
            Set.of("www.youtube.com", "youtu.be", "m.youtube.com");

    private final ProcessedVideoRecorder recorder;

    @GetMapping(value = "/processed", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    @ResponseBody
    public String list() {
        return renderPage(recorder.recent());
    }

    private String renderPage(List<ProcessedVideoEntry> entries) {
        StringBuilder body = new StringBuilder();
        body.append("<!doctype html><html><head><meta charset=\"utf-8\">")
                .append("<title>Processed videos</title>")
                .append("<style>body{font-family:monospace;margin:1rem;}")
                .append("table{width:100%;border-collapse:collapse;}")
                .append("th,td{border-bottom:1px solid #ddd;padding:.25rem .5rem;text-align:left;vertical-align:top;}")
                .append("th{background:#f4f4f4;}</style></head><body>");
        body.append("<h1>Processed videos (last ").append(entries.size()).append(")</h1>");

        if (entries.isEmpty()) {
            body.append("<p>No videos processed yet.</p>");
        } else {
            body.append("<table><thead><tr>")
                    .append("<th>When</th><th>Source</th><th>Title</th>")
                    .append("<th>Status</th><th>Tags</th><th>Collection</th>")
                    .append("</tr></thead><tbody>");
            for (ProcessedVideoEntry e : entries) {
                body.append("<tr>")
                        .append("<td>").append(esc(DateTimeFormatter.ISO_INSTANT.format(e.timestamp()))).append("</td>")
                        .append("<td>").append(esc(e.source().name().toLowerCase())).append("</td>")
                        .append("<td>").append(renderTitle(e.title(), e.url())).append("</td>")
                        .append("<td>").append(esc(e.status().name().toLowerCase())).append("</td>")
                        .append("<td>").append(renderTags(e.tags())).append("</td>")
                        .append("<td>").append(e.collection() == null ? "&mdash;" : esc(e.collection())).append("</td>")
                        .append("</tr>");
            }
            body.append("</tbody></table>");
        }

        body.append("</body></html>");
        return body.toString();
    }

    private String renderTitle(String title, String url) {
        String safeTitle = esc(title);
        if (isAllowedUrl(url)) {
            return "<a href=\"" + esc(url) + "\">" + safeTitle + "</a>";
        }
        return safeTitle;
    }

    private String renderTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(esc(tags.get(i)));
        }
        return out.toString();
    }

    private boolean isAllowedUrl(String url) {
        if (url == null) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme()) && ALLOWED_HOSTS.contains(uri.getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests 'org.bartram.vidtag.controller.ProcessedVideosControllerTest' -q`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/bartram/vidtag/controller/ProcessedVideosController.java \
        src/test/java/org/bartram/vidtag/controller/ProcessedVideosControllerTest.java
git commit -m "feat(processed): add GET /processed HTML view with XSS escaping + URL allowlist"
```

---

## Task 7: End-to-end async event flow integration test

**Files:**
- Create: `src/test/java/org/bartram/vidtag/service/VideoProcessedEventIT.java`

This verifies the actual async dispatch: publishing a `VideoProcessedEvent` reaches the `@Async @EventListener` and writes to Redis.

- [ ] **Step 1: Write the failing test**

`src/test/java/org/bartram/vidtag/service/VideoProcessedEventIT.java`:

```java
package org.bartram.vidtag.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.bartram.vidtag.TestcontainersConfiguration;
import org.bartram.vidtag.event.VideoProcessedEvent;
import org.bartram.vidtag.model.ProcessedVideoEntry;
import org.bartram.vidtag.model.ProcessingStatus;
import org.bartram.vidtag.model.Source;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VideoProcessedEventIT {

    @Autowired private ApplicationEventPublisher publisher;
    @Autowired private ProcessedVideoRecorder recorder;
    @Autowired private StringRedisTemplate redis;

    @AfterEach
    void cleanUp() {
        redis.delete(ProcessedVideoRecorder.KEY);
    }

    @Test
    void publishedEvent_reachesAsyncListener_andLandsInRedis() throws Exception {
        ProcessedVideoEntry entry = new ProcessedVideoEntry(
                Instant.parse("2026-05-11T20:14:33Z"),
                Source.PLAYLIST,
                "integration",
                "https://www.youtube.com/watch?v=int",
                ProcessingStatus.SUCCESS,
                List.of("test"),
                "Videos");

        publisher.publishEvent(new VideoProcessedEvent(entry));

        // Poll up to 2 seconds for the async listener to land the entry in Redis.
        List<ProcessedVideoEntry> recent = waitFor(() -> {
            List<ProcessedVideoEntry> r = recorder.recent();
            return r.isEmpty() ? null : r;
        }, 2_000);

        assertThat(recent).containsExactly(entry);
    }

    private <T> T waitFor(java.util.function.Supplier<T> s, long maxMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + maxMillis;
        while (System.currentTimeMillis() < deadline) {
            T value = s.get();
            if (value != null) {
                return value;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met within " + maxMillis + "ms");
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./gradlew test --tests 'org.bartram.vidtag.service.VideoProcessedEventIT' -q`
Expected: PASS.

If the test times out, suspect that `@EnableAsync` is not actually picking up the listener (check `VidtagApplication.java`), or that the `@EventListener` annotation got mis-imported (must be `org.springframework.context.event.EventListener`, not `jakarta.persistence`).

- [ ] **Step 3: Run the full test suite + spotless + jacoco**

Run: `./gradlew build -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/org/bartram/vidtag/service/VideoProcessedEventIT.java
git commit -m "test(processed): verify async event flow lands entries in Redis"
```

---

## Task 8: Manual browser verification

- [ ] **Step 1: Run the app locally**

Run: `./gradlew bootRun`

Wait for `Started VidtagApplication`.

- [ ] **Step 2: Verify the page renders**

Open `http://localhost:8080/processed` in a browser. With no videos processed yet, expect a page with the `<h1>Processed videos (last 0)</h1>` header and the placeholder *No videos processed yet.*

- [ ] **Step 3: Trigger a processing run**

Either:
- Wait for the playlist scheduler to fire (configured in `application.yaml`), or
- Trigger the unsorted processor: `curl -X POST http://localhost:8080/api/v1/unsorted/process`

Refresh `/processed`. Expect a table row per processed video.

- [ ] **Step 4: Stop the app**

`Ctrl-C` the `bootRun` process.

No commit for this task — it's a manual smoke check.

---

## Self-Review Notes

Spec coverage check:

- ✅ Both pipelines contribute — Tasks 4 + 5.
- ✅ Capped at 100, newest-first — Task 2 (`record101Entries_...` test).
- ✅ Survives app restart — Redis key not deleted on startup; verified implicitly by Tasks 2-3.
- ✅ HTML escaping for title/tags/collection — Task 6 (3 dedicated tests).
- ✅ URL allowlist (https + youtube hosts) — Task 6 (`nonYoutubeUrl_*`, `javascriptUrl_*` tests).
- ✅ Empty list placeholder — Task 6.
- ✅ Cell format: ISO-8601 timestamp, lowercase enums, comma-separated tags, em-dash for null collection — Task 6.
- ✅ `@Async @EventListener` decoupling — Task 7.
- ✅ Recording must never fail a pipeline — try/catch in `ProcessedVideoRecorder.record()` (Task 2).
- ✅ Read failure returns empty — try/catch in `recent()` (Task 2). No dedicated test for Redis-down (covered by code inspection); malformed-JSON skip has a dedicated test (Task 3).
- ✅ No new runtime dependencies — verified: `HtmlUtils`, `StringRedisTemplate`, `ObjectMapper`, `ApplicationEventPublisher` all already on the classpath.
- ✅ Plan / spec location matches project convention — `docs/plans/<date>-*.md`.

No placeholders. No "TODO" or "TBD" steps. No undefined symbols (e.g., `ProcessedVideoRecorder.KEY` is referenced in Task 7 and exposed as `static final String KEY` in Task 2).

Type / signature consistency check:

- `ProcessedVideoRecorder.record(ProcessedVideoEntry)` and `ProcessedVideoRecorder.recent()` are referenced identically across Tasks 2, 3, 4, 5, 6, 7.
- `VideoProcessedEvent(ProcessedVideoEntry entry)` — Tasks 1, 4, 5, 7.
- `Source.PLAYLIST` / `Source.UNSORTED` — consistent across producer wirings.
- `ProcessedVideoEntry` constructor argument order matches between definition (Task 1) and all callers.
