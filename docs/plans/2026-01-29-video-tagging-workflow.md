# Video Tagging Workflow Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a REST API that retrieves YouTube playlist videos, uses Claude AI to determine tags from existing Raindrop.io tags, and saves bookmarks with real-time SSE progress streaming.

**Architecture:** Traditional Spring Boot service layer with SSE streaming, circuit breakers on external APIs, Redis caching for Raindrop tags, batch processing (10 videos at a time), and comprehensive retry/error handling.

**Tech Stack:** Spring Boot 4.0.2, Java 21, Spring AI (Anthropic Claude), Spring Cloud Circuit Breaker (Resilience4J), Redis, Spring Cache, SSE, Testcontainers

---

## Task 1: Data Models and DTOs

**Files:**
- Create: `src/main/java/org/bartram/vidtag/model/VideoFilters.java`
- Create: `src/main/java/org/bartram/vidtag/model/TagStrategy.java`
- Create: `src/main/java/org/bartram/vidtag/model/Verbosity.java`
- Create: `src/main/java/org/bartram/vidtag/dto/TagPlaylistRequest.java`
- Create: `src/main/java/org/bartram/vidtag/model/VideoMetadata.java`
- Create: `src/main/java/org/bartram/vidtag/model/TagWithConfidence.java`
- Create: `src/main/java/org/bartram/vidtag/model/ProcessingStatus.java`
- Create: `src/main/java/org/bartram/vidtag/model/VideoProcessingResult.java`
- Create: `src/main/java/org/bartram/vidtag/model/ProcessingSummary.java`

**Step 1: Write test for request validation**

Create: `src/test/java/org/bartram/vidtag/dto/TagPlaylistRequestTest.java`

```java
package org.bartram.vidtag.dto;

import org.bartram.vidtag.model.TagStrategy;
import org.bartram.vidtag.model.Verbosity;
import org.bartram.vidtag.model.VideoFilters;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TagPlaylistRequestTest {

    @Test
    void shouldCreateValidRequest() {
        var filters = new VideoFilters(
            Instant.parse("2024-01-01T00:00:00Z"),
            3600,
            50
        );
        var tagStrategy = new TagStrategy(5, 0.7, "Focus on technical topics");
        
        var request = new TagPlaylistRequest(
            "PLxxx",
            12345L,
            filters,
            tagStrategy,
            Verbosity.DETAILED
        );
        
        assertThat(request.playlistInput()).isEqualTo("PLxxx");
        assertThat(request.raindropCollectionTitle()).isEqualTo("My Videos");
        assertThat(request.filters()).isNotNull();
        assertThat(request.tagStrategy()).isNotNull();
        assertThat(request.verbosity()).isEqualTo(Verbosity.DETAILED);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'org.bartram.vidtag.dto.TagPlaylistRequestTest'`
Expected: FAIL with compilation errors (classes don't exist)

**Step 3: Create Verbosity enum**

Create: `src/main/java/org/bartram/vidtag/model/Verbosity.java`

```java
package org.bartram.vidtag.model;

public enum Verbosity {
    MINIMAL,
    STANDARD,
    DETAILED,
    VERBOSE
}
```

**Step 4: Create VideoFilters record**

Create: `src/main/java/org/bartram/vidtag/model/VideoFilters.java`

```java
package org.bartram.vidtag.model;

import java.time.Instant;

public record VideoFilters(
    Instant publishedAfter,
    Integer maxDuration,
    Integer maxVideos
) {}
```

**Step 5: Create TagStrategy record**

Create: `src/main/java/org/bartram/vidtag/model/TagStrategy.java`

```java
package org.bartram.vidtag.model;

public record TagStrategy(
    Integer maxTagsPerVideo,
    Double confidenceThreshold,
    String customInstructions
) {}
```

**Step 6: Create TagPlaylistRequest record**

Create: `src/main/java/org/bartram/vidtag/dto/TagPlaylistRequest.java`

```java
package org.bartram.vidtag.dto;

import org.bartram.vidtag.model.TagStrategy;
import org.bartram.vidtag.model.Verbosity;
import org.bartram.vidtag.model.VideoFilters;

public record TagPlaylistRequest(
    String playlistInput,
    String raindropCollectionTitle,
    VideoFilters filters,
    TagStrategy tagStrategy,
    Verbosity verbosity
) {}
```

**Step 7: Run test to verify it passes**

Run: `./gradlew test --tests 'org.bartram.vidtag.dto.TagPlaylistRequestTest'`
Expected: PASS

**Step 8: Create domain model tests**

Create: `src/test/java/org/bartram/vidtag/model/VideoMetadataTest.java`

```java
package org.bartram.vidtag.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class VideoMetadataTest {

    @Test
    void shouldCreateVideoMetadata() {
        var metadata = new VideoMetadata(
            "abc123",
            "https://youtube.com/watch?v=abc123",
            "Test Video",
            "Test Description",
            Instant.now(),
            300
        );
        
        assertThat(metadata.videoId()).isEqualTo("abc123");
        assertThat(metadata.url()).contains("abc123");
        assertThat(metadata.title()).isEqualTo("Test Video");
        assertThat(metadata.duration()).isEqualTo(300);
    }
}
```

**Step 9: Run test to verify it fails**

Run: `./gradlew test --tests 'org.bartram.vidtag.model.VideoMetadataTest'`
Expected: FAIL (VideoMetadata doesn't exist)

**Step 10: Create VideoMetadata record**

Create: `src/main/java/org/bartram/vidtag/model/VideoMetadata.java`

```java
package org.bartram.vidtag.model;

import java.time.Instant;

public record VideoMetadata(
    String videoId,
    String url,
    String title,
    String description,
    Instant publishedAt,
    Integer duration
) {}
```

**Step 11: Create ProcessingStatus enum**

Create: `src/main/java/org/bartram/vidtag/model/ProcessingStatus.java`

```java
package org.bartram.vidtag.model;

public enum ProcessingStatus {
    SUCCESS,
    SKIPPED,
    FAILED
}
```

**Step 12: Create TagWithConfidence record**

Create: `src/main/java/org/bartram/vidtag/model/TagWithConfidence.java`

```java
package org.bartram.vidtag.model;

public record TagWithConfidence(
    String tag,
    Double confidence,
    Boolean isExisting
) {}
```

**Step 13: Create VideoProcessingResult record**

Create: `src/main/java/org/bartram/vidtag/model/VideoProcessingResult.java`

```java
package org.bartram.vidtag.model;

import java.util.List;

public record VideoProcessingResult(
    VideoMetadata video,
    List<TagWithConfidence> selectedTags,
    ProcessingStatus status,
    String errorMessage
) {}
```

**Step 14: Create ProcessingSummary record**

Create: `src/main/java/org/bartram/vidtag/model/ProcessingSummary.java`

```java
package org.bartram.vidtag.model;

public record ProcessingSummary(
    Integer totalVideos,
    Integer succeeded,
    Integer skipped,
    Integer failed
) {}
```

**Step 15: Run all tests**

Run: `./gradlew test`
Expected: All tests PASS

**Step 16: Commit data models**

```bash
git add src/main/java/org/bartram/vidtag/model/ src/main/java/org/bartram/vidtag/dto/ src/test/java/org/bartram/vidtag/
git commit -m "feat: add data models and DTOs for video tagging workflow"
```

---

## Task 2: YouTube Service with Circuit Breaker

**Files:**
- Create: `src/main/java/org/bartram/vidtag/service/YouTubeService.java`
- Create: `src/test/java/org/bartram/vidtag/service/YouTubeServiceTest.java`
- Modify: `src/main/resources/application.yaml` (add YouTube config)

**Step 1: Write test for playlist ID extraction from URL**

Create: `src/test/java/org/bartram/vidtag/service/YouTubeServiceTest.java`

```java
package org.bartram.vidtag.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class YouTubeServiceTest {

    @InjectMocks
    private YouTubeService youTubeService;

    @Test
    void shouldExtractPlaylistIdFromUrl() {
        String url = "https://youtube.com/playlist?list=PLxxx123";
        String playlistId = youTubeService.extractPlaylistId(url);
        assertThat(playlistId).isEqualTo("PLxxx123");
    }

    @Test
    void shouldReturnPlaylistIdWhenAlreadyId() {
        String id = "PLxxx123";
        String playlistId = youTubeService.extractPlaylistId(id);
        assertThat(playlistId).isEqualTo("PLxxx123");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'org.bartram.vidtag.service.YouTubeServiceTest'`
Expected: FAIL (YouTubeService doesn't exist)

**Step 3: Create YouTubeService with extractPlaylistId method**

Create: `src/main/java/org/bartram/vidtag/service/YouTubeService.java`

```java
package org.bartram.vidtag.service;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class YouTubeService {

    private static final Pattern PLAYLIST_URL_PATTERN = 
        Pattern.compile("(?:list=)([a-zA-Z0-9_-]+)");

    public String extractPlaylistId(String playlistInput) {
        var matcher = PLAYLIST_URL_PATTERN.matcher(playlistInput);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return playlistInput; // Already an ID
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests 'org.bartram.vidtag.service.YouTubeServiceTest'`
Expected: PASS

**Step 5: Add test for fetching playlist videos (mocked)**

Add to `YouTubeServiceTest.java`:

```java
import org.bartram.vidtag.model.VideoFilters;
import org.bartram.vidtag.model.VideoMetadata;
import org.mockito.Mock;

import java.time.Instant;
import java.util.List;

// Add to class:
@Mock
private YouTubeApiClient youTubeApiClient; // We'll create this interface

@Test
void shouldFetchPlaylistVideos() {
    String playlistId = "PLxxx123";
    VideoFilters filters = new VideoFilters(null, null, null);
    
    List<VideoMetadata> videos = youTubeService.fetchPlaylistVideos(playlistId, filters);
    
    assertThat(videos).isNotNull();
}
```

**Step 6: Run test to verify it fails**

Run: `./gradlew test --tests 'org.bartram.vidtag.service.YouTubeServiceTest'`
Expected: FAIL (method doesn't exist)

**Step 7: Create YouTubeApiClient interface**

Create: `src/main/java/org/bartram/vidtag/client/YouTubeApiClient.java`

```java
package org.bartram.vidtag.client;

import org.bartram.vidtag.model.VideoMetadata;

import java.util.List;

public interface YouTubeApiClient {
    List<VideoMetadata> getPlaylistVideos(String playlistId);
}
```

**Step 8: Add fetchPlaylistVideos method with circuit breaker**

Modify: `src/main/java/org/bartram/vidtag/service/YouTubeService.java`

```java
package org.bartram.vidtag.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.bartram.vidtag.client.YouTubeApiClient;
import org.bartram.vidtag.model.VideoFilters;
import org.bartram.vidtag.model.VideoMetadata;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class YouTubeService {

    private static final Pattern PLAYLIST_URL_PATTERN = 
        Pattern.compile("(?:list=)([a-zA-Z0-9_-]+)");

    private final YouTubeApiClient youTubeApiClient;

    public YouTubeService(YouTubeApiClient youTubeApiClient) {
        this.youTubeApiClient = youTubeApiClient;
    }

    public String extractPlaylistId(String playlistInput) {
        var matcher = PLAYLIST_URL_PATTERN.matcher(playlistInput);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return playlistInput;
    }

    @CircuitBreaker(name = "youtube", fallbackMethod = "fetchPlaylistVideosFallback")
    public List<VideoMetadata> fetchPlaylistVideos(String playlistId, VideoFilters filters) {
        List<VideoMetadata> videos = youTubeApiClient.getPlaylistVideos(playlistId);
        return applyFilters(videos, filters);
    }

    private List<VideoMetadata> applyFilters(List<VideoMetadata> videos, VideoFilters filters) {
        var stream = videos.stream();

        if (filters.publishedAfter() != null) {
            stream = stream.filter(v -> v.publishedAt().isAfter(filters.publishedAfter()));
        }

        if (filters.maxDuration() != null) {
            stream = stream.filter(v -> v.duration() <= filters.maxDuration());
        }

        if (filters.maxVideos() != null) {
            stream = stream.limit(filters.maxVideos());
        }

        return stream.collect(Collectors.toList());
    }

    private List<VideoMetadata> fetchPlaylistVideosFallback(String playlistId, VideoFilters filters, Exception ex) {
        throw new RuntimeException("Failed to fetch playlist videos after retries", ex);
    }
}
```

**Step 9: Update test with mock setup**

Modify: `src/test/java/org/bartram/vidtag/service/YouTubeServiceTest.java`

```java
package org.bartram.vidtag.service;

import org.bartram.vidtag.client.YouTubeApiClient;
import org.bartram.vidtag.model.VideoFilters;
import org.bartram.vidtag.model.VideoMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YouTubeServiceTest {

    @Mock
    private YouTubeApiClient youTubeApiClient;

    @InjectMocks
    private YouTubeService youTubeService;

    @Test
    void shouldExtractPlaylistIdFromUrl() {
        String url = "https://youtube.com/playlist?list=PLxxx123";
        String playlistId = youTubeService.extractPlaylistId(url);
        assertThat(playlistId).isEqualTo("PLxxx123");
    }

    @Test
    void shouldReturnPlaylistIdWhenAlreadyId() {
        String id = "PLxxx123";
        String playlistId = youTubeService.extractPlaylistId(id);
        assertThat(playlistId).isEqualTo("PLxxx123");
    }

    @Test
    void shouldFetchPlaylistVideos() {
        String playlistId = "PLxxx123";
        VideoFilters filters = new VideoFilters(null, null, null);
        
        var mockVideos = List.of(
            new VideoMetadata("v1", "url1", "Video 1", "desc", Instant.now(), 300),
            new VideoMetadata("v2", "url2", "Video 2", "desc", Instant.now(), 600)
        );
        when(youTubeApiClient.getPlaylistVideos(playlistId)).thenReturn(mockVideos);
        
        List<VideoMetadata> videos = youTubeService.fetchPlaylistVideos(playlistId, filters);
        
        assertThat(videos).hasSize(2);
    }

    @Test
    void shouldFilterByMaxDuration() {
        String playlistId = "PLxxx123";
        VideoFilters filters = new VideoFilters(null, 400, null);
        
        var mockVideos = List.of(
            new VideoMetadata("v1", "url1", "Video 1", "desc", Instant.now(), 300),
            new VideoMetadata("v2", "url2", "Video 2", "desc", Instant.now(), 600)
        );
        when(youTubeApiClient.getPlaylistVideos(playlistId)).thenReturn(mockVideos);
        
        List<VideoMetadata> videos = youTubeService.fetchPlaylistVideos(playlistId, filters);
        
        assertThat(videos).hasSize(1);
        assertThat(videos.get(0).videoId()).isEqualTo("v1");
    }
}
```

**Step 10: Run tests**

Run: `./gradlew test --tests 'org.bartram.vidtag.service.YouTubeServiceTest'`
Expected: PASS

**Step 11: Add Resilience4J configuration**

Modify: `src/main/resources/application.yaml`

```yaml
spring:
  application:
    name: vidtag

resilience4j:
  circuitbreaker:
    instances:
      youtube:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        sliding-window-size: 10
        sliding-window-type: COUNT_BASED
      raindrop:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        sliding-window-size: 10
        sliding-window-type: COUNT_BASED
      claude:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        sliding-window-size: 10
        sliding-window-type: COUNT_BASED
        
  retry:
    instances:
      youtube:
        max-attempts: 3
        wait-duration: 1s
        exponential-backoff-multiplier: 2
      raindrop:
        max-attempts: 3
        wait-duration: 1s
        exponential-backoff-multiplier: 2
      claude:
        max-attempts: 2
        wait-duration: 1s
        exponential-backoff-multiplier: 2
```

**Step 12: Run all tests**

Run: `./gradlew test`
Expected: All tests PASS

**Step 13: Commit YouTube service**

```bash
git add src/main/java/org/bartram/vidtag/service/YouTubeService.java src/main/java/org/bartram/vidtag/client/YouTubeApiClient.java src/test/java/org/bartram/vidtag/service/YouTubeServiceTest.java src/main/resources/application.yaml
git commit -m "feat: add YouTube service with circuit breaker and filtering"
```

---

## Task 3: Raindrop Service with Caching

**Files:**
- Create: `src/main/java/org/bartram/vidtag/client/RaindropApiClient.java`
- Create: `src/main/java/org/bartram/vidtag/model/RaindropTag.java`
- Create: `src/main/java/org/bartram/vidtag/service/RaindropService.java`
- Create: `src/test/java/org/bartram/vidtag/service/RaindropServiceTest.java`
- Modify: `src/main/resources/application.yaml` (add cache config)

**Step 1: Write test for fetching tags with cache**

Create: `src/test/java/org/bartram/vidtag/service/RaindropServiceTest.java`

```java
package org.bartram.vidtag.service;

import org.bartram.vidtag.client.RaindropApiClient;
import org.bartram.vidtag.model.RaindropTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RaindropServiceTest {

    @Mock
    private RaindropApiClient raindropApiClient;

    @InjectMocks
    private RaindropService raindropService;

    @Test
    void shouldFetchUserTags() {
        String userId = "user123";
        var mockTags = List.of(
            new RaindropTag("java"),
            new RaindropTag("spring")
        );
        when(raindropApiClient.getUserTags(userId)).thenReturn(mockTags);

        List<RaindropTag> tags = raindropService.getUserTags(userId);

        assertThat(tags).hasSize(2);
        verify(raindropApiClient, times(1)).getUserTags(userId);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'org.bartram.vidtag.service.RaindropServiceTest'`
Expected: FAIL (classes don't exist)

**Step 3: Create RaindropTag model**

Create: `src/main/java/org/bartram/vidtag/model/RaindropTag.java`

```java
package org.bartram.vidtag.model;

public record RaindropTag(String name) {}
```

**Step 3a: Create RaindropCollection model**

Create: `src/main/java/org/bartram/vidtag/model/RaindropCollection.java`

```java
package org.bartram.vidtag.model;

public record RaindropCollection(
    Long id,
    String title
) {}
```

**Step 4: Create RaindropApiClient interface**

Create: `src/main/java/org/bartram/vidtag/client/RaindropApiClient.java`

```java
package org.bartram.vidtag.client;

import org.bartram.vidtag.model.RaindropTag;
import org.bartram.vidtag.model.VideoMetadata;

import java.util.List;

public interface RaindropApiClient {
    List<RaindropTag> getUserTags(String userId);
    List<RaindropCollection> getUserCollections(String userId);
    boolean bookmarkExists(Long collectionId, String url);
    void createBookmark(Long collectionId, String url, String title, List<String> tags);
}
```

**Step 5: Create RaindropService with caching**

Create: `src/main/java/org/bartram/vidtag/service/RaindropService.java`

```java
package org.bartram.vidtag.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.bartram.vidtag.client.RaindropApiClient;
import org.bartram.vidtag.model.RaindropTag;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RaindropService {

    private final RaindropApiClient raindropApiClient;

    public RaindropService(RaindropApiClient raindropApiClient) {
        this.raindropApiClient = raindropApiClient;
    }

    @Cacheable(value = "raindrop-tags", key = "#userId")
    @CircuitBreaker(name = "raindrop", fallbackMethod = "getUserTagsFallback")
    public List<RaindropTag> getUserTags(String userId) {
        return raindropApiClient.getUserTags(userId);
    }

    @CircuitBreaker(name = "raindrop", fallbackMethod = "resolveCollectionIdFallback")
    public Long resolveCollectionId(String userId, String collectionTitle) {
        List<RaindropCollection> collections = raindropApiClient.getUserCollections(userId);
        return collections.stream()
                .filter(c -> c.title().equalsIgnoreCase(collectionTitle))
                .findFirst()
                .map(RaindropCollection::id)
                .orElseThrow(() -> new IllegalArgumentException("Collection not found: " + collectionTitle));
    }

    @CircuitBreaker(name = "raindrop", fallbackMethod = "bookmarkExistsFallback")
    public boolean bookmarkExists(Long collectionId, String url) {
        return raindropApiClient.bookmarkExists(collectionId, url);
    }

    @CircuitBreaker(name = "raindrop", fallbackMethod = "createBookmarkFallback")
    public void createBookmark(Long collectionId, String url, String title, List<String> tags) {
        raindropApiClient.createBookmark(collectionId, url, title, tags);
    }

    private List<RaindropTag> getUserTagsFallback(String userId, Exception ex) {
        throw new RuntimeException("Failed to fetch Raindrop tags after retries", ex);
    }

    private boolean bookmarkExistsFallback(Long collectionId, String url, Exception ex) {
        throw new RuntimeException("Failed to check bookmark existence after retries", ex);
    }

    private void createBookmarkFallback(Long collectionId, String url, String title, List<String> tags, Exception ex) {
        throw new RuntimeException("Failed to create bookmark after retries", ex);
    }

    private Long resolveCollectionIdFallback(String userId, String collectionTitle, Exception ex) {
        throw new RuntimeException("Failed to resolve collection ID after retries", ex);
    }
}
```

**Step 6: Run test to verify it passes**

Run: `./gradlew test --tests 'org.bartram.vidtag.service.RaindropServiceTest'`
Expected: PASS

**Step 7: Add test for collection resolution**

Add to `RaindropServiceTest.java`:

```java
import org.bartram.vidtag.model.RaindropCollection;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Test
void shouldResolveCollectionIdFromTitle() {
    String userId = "user123";
    String title = "My Videos";
    var mockCollections = List.of(
        new RaindropCollection(1L, "Work"),
        new RaindropCollection(2L, "My Videos"),
        new RaindropCollection(3L, "Learning")
    );
    when(raindropApiClient.getUserCollections(userId)).thenReturn(mockCollections);

    Long collectionId = raindropService.resolveCollectionId(userId, title);

    assertThat(collectionId).isEqualTo(2L);
}

@Test
void shouldThrowExceptionWhenCollectionNotFound() {
    String userId = "user123";
    String title = "NonExistent";
    when(raindropApiClient.getUserCollections(userId)).thenReturn(List.of());

    assertThatThrownBy(() -> raindropService.resolveCollectionId(userId, title))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Collection not found");
}
```

**Step 8: Add test for duplicate checking**

Add to `RaindropServiceTest.java`:

```java
@Test
void shouldCheckIfBookmarkExists() {
    Long collectionId = 123L;
    String url = "https://youtube.com/watch?v=abc";
    
    when(raindropApiClient.bookmarkExists(collectionId, url)).thenReturn(true);
    
    boolean exists = raindropService.bookmarkExists(collectionId, url);
    
    assertThat(exists).isTrue();
}

@Test
void shouldCreateBookmark() {
    Long collectionId = 123L;
    String url = "https://youtube.com/watch?v=abc";
    String title = "Test Video";
    List<String> tags = List.of("java", "spring");
    
    raindropService.createBookmark(collectionId, url, title, tags);
    
    verify(raindropApiClient, times(1)).createBookmark(collectionId, url, title, tags);
}
```

**Step 9: Run tests**

Run: `./gradlew test --tests 'org.bartram.vidtag.service.RaindropServiceTest'`
Expected: PASS

**Step 10: Add Redis cache configuration**

Modify: `src/main/resources/application.yaml`

Add under `spring:`:

```yaml
  cache:
    type: redis
    redis:
      time-to-live: 900000  # 15 minutes in milliseconds
      cache-null-values: false
  data:
    redis:
      host: localhost
      port: 6379
```

**Step 11: Enable caching in application**

Modify: `src/main/java/org/bartram/vidtag/VidtagApplication.java`

```java
package org.bartram.vidtag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class VidtagApplication {

    public static void main(String[] args) {
        SpringApplication.run(VidtagApplication.class, args);
    }

}
```

**Step 12: Run all tests**

Run: `./gradlew test`
Expected: All tests PASS

**Step 13: Commit Raindrop service**

```bash
git add src/main/java/org/bartram/vidtag/service/RaindropService.java src/main/java/org/bartram/vidtag/client/RaindropApiClient.java src/main/java/org/bartram/vidtag/model/RaindropTag.java src/main/java/org/bartram/vidtag/model/RaindropCollection.java src/test/java/org/bartram/vidtag/service/RaindropServiceTest.java src/main/resources/application.yaml src/main/java/org/bartram/vidtag/VidtagApplication.java
git commit -m "feat: add Raindrop service with collection resolution, caching and circuit breaker"
```

---

## Task 4: Video Tagging Service with Spring AI

**Files:**
- Create: `src/main/java/org/bartram/vidtag/service/VideoTaggingService.java`
- Create: `src/test/java/org/bartram/vidtag/service/VideoTaggingServiceTest.java`

**Step 1: Write test for tag generation**

Create: `src/test/java/org/bartram/vidtag/service/VideoTaggingServiceTest.java`

```java
package org.bartram.vidtag.service;

import org.bartram.vidtag.model.RaindropTag;
import org.bartram.vidtag.model.TagStrategy;
import org.bartram.vidtag.model.TagWithConfidence;
import org.bartram.vidtag.model.VideoMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.RequestResponseSpec;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoTaggingServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @InjectMocks
    private VideoTaggingService videoTaggingService;

    @Test
    void shouldGenerateTagsForVideo() {
        var video = new VideoMetadata(
            "v1",
            "url",
            "Spring Boot Tutorial",
            "Learn Spring Boot",
            Instant.now(),
            300
        );
        var existingTags = List.of(
            new RaindropTag("java"),
            new RaindropTag("spring"),
            new RaindropTag("tutorial")
        );
        var tagStrategy = new TagStrategy(3, 0.7, null);

        var chatClient = mock(ChatClient.class);
        var requestResponseSpec = mock(RequestResponseSpec.class);
        
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(mock(ChatClient.ChatClientRequest.class));
        // Simplified mock - actual implementation will be more complex
        
        List<TagWithConfidence> tags = videoTaggingService.generateTags(video, existingTags, tagStrategy);

        assertThat(tags).isNotNull();
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'org.bartram.vidtag.service.VideoTaggingServiceTest'`
Expected: FAIL (VideoTaggingService doesn't exist)

**Step 3: Create VideoTaggingService**

Create: `src/main/java/org/bartram/vidtag/service/VideoTaggingService.java`

```java
package org.bartram.vidtag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.bartram.vidtag.model.RaindropTag;
import org.bartram.vidtag.model.TagStrategy;
import org.bartram.vidtag.model.TagWithConfidence;
import org.bartram.vidtag.model.VideoMetadata;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class VideoTaggingService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public VideoTaggingService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = new ObjectMapper();
    }

    @CircuitBreaker(name = "claude", fallbackMethod = "generateTagsFallback")
    public List<TagWithConfidence> generateTags(
            VideoMetadata video,
            List<RaindropTag> existingTags,
            TagStrategy tagStrategy) {

        String existingTagsList = existingTags.stream()
                .map(RaindropTag::name)
                .toList()
                .toString();

        String prompt = buildPrompt(video, existingTagsList, tagStrategy);

        String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        return parseTagsFromResponse(response, existingTags, tagStrategy);
    }

    private String buildPrompt(VideoMetadata video, String existingTags, TagStrategy tagStrategy) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze this video and suggest relevant tags.\n\n");
        prompt.append("Video Title: ").append(video.title()).append("\n");
        prompt.append("Description: ").append(video.description()).append("\n\n");
        prompt.append("Available tags: ").append(existingTags).append("\n\n");
        prompt.append("Rules:\n");
        prompt.append("1. Prefer tags from the available tags list\n");
        prompt.append("2. Only suggest new tags if existing ones don't fit well AND confidence is high\n");
        prompt.append("3. Maximum tags: ").append(tagStrategy.maxTagsPerVideo()).append("\n");
        prompt.append("4. Minimum confidence threshold: ").append(tagStrategy.confidenceThreshold()).append("\n");
        
        if (tagStrategy.customInstructions() != null) {
            prompt.append("5. Additional instructions: ").append(tagStrategy.customInstructions()).append("\n");
        }

        prompt.append("\nRespond with JSON array of objects with fields: tag (string), confidence (0.0-1.0), isExisting (boolean)\n");
        prompt.append("Example: [{\"tag\":\"java\",\"confidence\":0.95,\"isExisting\":true}]");

        return prompt.toString();
    }

    private List<TagWithConfidence> parseTagsFromResponse(
            String response,
            List<RaindropTag> existingTags,
            TagStrategy tagStrategy) {

        try {
            // Extract JSON from response (may be wrapped in markdown code blocks)
            String json = response;
            if (response.contains("```json")) {
                json = response.substring(response.indexOf("["), response.lastIndexOf("]") + 1);
            } else if (response.contains("```")) {
                json = response.substring(response.indexOf("["), response.lastIndexOf("]") + 1);
            }

            List<Map<String, Object>> tagMaps = objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
            );

            List<TagWithConfidence> tags = new ArrayList<>();
            for (Map<String, Object> tagMap : tagMaps) {
                String tag = (String) tagMap.get("tag");
                Double confidence = ((Number) tagMap.get("confidence")).doubleValue();
                Boolean isExisting = (Boolean) tagMap.get("isExisting");

                // Apply confidence threshold
                if (confidence >= tagStrategy.confidenceThreshold()) {
                    tags.add(new TagWithConfidence(tag, confidence, isExisting));
                }

                // Respect max tags limit
                if (tags.size() >= tagStrategy.maxTagsPerVideo()) {
                    break;
                }
            }

            return tags;

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse AI response", e);
        }
    }

    private List<TagWithConfidence> generateTagsFallback(
            VideoMetadata video,
            List<RaindropTag> existingTags,
            TagStrategy tagStrategy,
            Exception ex) {
        throw new RuntimeException("Failed to generate tags after retries", ex);
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests 'org.bartram.vidtag.service.VideoTaggingServiceTest'`
Expected: PASS (may need to refine mocks)

**Step 5: Add integration test with real AI (manual verification)**

Create: `src/test/java/org/bartram/vidtag/service/VideoTaggingServiceIntegrationTest.java`

```java
package org.bartram.vidtag.service;

import org.bartram.vidtag.model.RaindropTag;
import org.bartram.vidtag.model.TagStrategy;
import org.bartram.vidtag.model.TagWithConfidence;
import org.bartram.vidtag.model.VideoMetadata;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Disabled("Integration test - requires API key")
class VideoTaggingServiceIntegrationTest {

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Test
    void shouldGenerateTagsWithRealAI() {
        var service = new VideoTaggingService(chatClientBuilder);
        
        var video = new VideoMetadata(
            "v1",
            "url",
            "Introduction to Spring Boot 3",
            "Learn the basics of Spring Boot 3 framework",
            Instant.now(),
            300
        );
        var existingTags = List.of(
            new RaindropTag("java"),
            new RaindropTag("spring"),
            new RaindropTag("tutorial"),
            new RaindropTag("framework")
        );
        var tagStrategy = new TagStrategy(3, 0.7, "Focus on programming topics");

        List<TagWithConfidence> tags = service.generateTags(video, existingTags, tagStrategy);

        assertThat(tags).isNotEmpty();
        assertThat(tags).hasSizeLessThanOrEqualTo(3);
        tags.forEach(tag -> {
            assertThat(tag.confidence()).isGreaterThanOrEqualTo(0.7);
        });
    }
}
```

**Step 6: Run all tests**

Run: `./gradlew test`
Expected: All tests PASS (integration test skipped)

**Step 7: Commit video tagging service**

```bash
git add src/main/java/org/bartram/vidtag/service/VideoTaggingService.java src/test/java/org/bartram/vidtag/service/
git commit -m "feat: add video tagging service with Spring AI integration"
```

---

## Task 5: Video Tagging Orchestrator

**Files:**
- Create: `src/main/java/org/bartram/vidtag/service/VideoTaggingOrchestrator.java`
- Create: `src/main/java/org/bartram/vidtag/event/ProgressEvent.java`
- Create: `src/test/java/org/bartram/vidtag/service/VideoTaggingOrchestratorTest.java`

**Step 1: Create ProgressEvent model**

Create: `src/main/java/org/bartram/vidtag/event/ProgressEvent.java`

```java
package org.bartram.vidtag.event;

public record ProgressEvent(
    String eventType,
    String message,
    Object data
) {
    public static ProgressEvent started() {
        return new ProgressEvent("started", "Processing started", null);
    }

    public static ProgressEvent progress(String message, Object data) {
        return new ProgressEvent("progress", message, data);
    }

    public static ProgressEvent videoCompleted(Object data) {
        return new ProgressEvent("video_completed", "Video processed", data);
    }

    public static ProgressEvent videoSkipped(String videoTitle) {
        return new ProgressEvent("video_skipped", "Video skipped: " + videoTitle, null);
    }

    public static ProgressEvent batchCompleted(int batchNumber) {
        return new ProgressEvent("batch_completed", "Batch " + batchNumber + " completed", null);
    }

    public static ProgressEvent error(String message) {
        return new ProgressEvent("error", message, null);
    }

    public static ProgressEvent completed(Object summary) {
        return new ProgressEvent("completed", "Processing completed", summary);
    }
}
```

**Step 2: Write test for orchestrator**

Create: `src/test/java/org/bartram/vidtag/service/VideoTaggingOrchestratorTest.java`

```java
package org.bartram.vidtag.service;

import org.bartram.vidtag.dto.TagPlaylistRequest;
import org.bartram.vidtag.event.ProgressEvent;
import org.bartram.vidtag.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoTaggingOrchestratorTest {

    @Mock
    private YouTubeService youTubeService;

    @Mock
    private RaindropService raindropService;

    @Mock
    private VideoTaggingService videoTaggingService;

    @InjectMocks
    private VideoTaggingOrchestrator orchestrator;

    @Test
    void shouldProcessPlaylistSuccessfully() {
        var request = new TagPlaylistRequest(
            "PLxxx",
            123L,
            new VideoFilters(null, null, null),
            new TagStrategy(3, 0.7, null),
            Verbosity.STANDARD
        );

        var videos = List.of(
            new VideoMetadata("v1", "url1", "Video 1", "desc", Instant.now(), 300)
        );

        var tags = List.of(new RaindropTag("java"));
        var selectedTags = List.of(new TagWithConfidence("java", 0.9, true));

        when(youTubeService.extractPlaylistId("PLxxx")).thenReturn("PLxxx");
        when(youTubeService.fetchPlaylistVideos("PLxxx", request.filters())).thenReturn(videos);
        when(raindropService.getUserTags(any())).thenReturn(tags);
        when(raindropService.bookmarkExists(any(), any())).thenReturn(false);
        when(videoTaggingService.generateTags(any(), any(), any())).thenReturn(selectedTags);

        List<ProgressEvent> events = new ArrayList<>();
        Consumer<ProgressEvent> eventEmitter = events::add;

        orchestrator.processPlaylist(request, eventEmitter);

        verify(youTubeService).fetchPlaylistVideos("PLxxx", request.filters());
        verify(raindropService).resolveCollectionId(any(), eq("My Videos"));
        verify(raindropService).createBookmark(any(), eq("url1"), eq("Video 1"), any());
    }
}
```

**Step 3: Run test to verify it fails**

Run: `./gradlew test --tests 'org.bartram.vidtag.service.VideoTaggingOrchestratorTest'`
Expected: FAIL (VideoTaggingOrchestrator doesn't exist)

**Step 4: Create VideoTaggingOrchestrator**

Create: `src/main/java/org/bartram/vidtag/service/VideoTaggingOrchestrator.java`

```java
package org.bartram.vidtag.service;

import org.bartram.vidtag.dto.TagPlaylistRequest;
import org.bartram.vidtag.event.ProgressEvent;
import org.bartram.vidtag.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class VideoTaggingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(VideoTaggingOrchestrator.class);
    private static final int BATCH_SIZE = 10;
    private static final String DEFAULT_USER_ID = "default"; // TODO: Get from auth context

    private final YouTubeService youTubeService;
    private final RaindropService raindropService;
    private final VideoTaggingService videoTaggingService;

    public VideoTaggingOrchestrator(
            YouTubeService youTubeService,
            RaindropService raindropService,
            VideoTaggingService videoTaggingService) {
        this.youTubeService = youTubeService;
        this.raindropService = raindropService;
        this.videoTaggingService = videoTaggingService;
    }

    @Async
    public void processPlaylist(TagPlaylistRequest request, Consumer<ProgressEvent> eventEmitter) {
        try {
            eventEmitter.accept(ProgressEvent.started());

            // Extract playlist ID
            String playlistId = youTubeService.extractPlaylistId(request.playlistInput());

            // Resolve collection title to ID
            Long collectionId = raindropService.resolveCollectionId(DEFAULT_USER_ID, request.raindropCollectionTitle());

            // Fetch and cache Raindrop tags
            List<RaindropTag> existingTags = raindropService.getUserTags(DEFAULT_USER_ID);

            // Fetch playlist videos with filters
            List<VideoMetadata> videos = youTubeService.fetchPlaylistVideos(playlistId, request.filters());

            // Process in batches
            List<VideoProcessingResult> results = new ArrayList<>();
            List<List<VideoMetadata>> batches = partition(videos, BATCH_SIZE);

            for (int i = 0; i < batches.size(); i++) {
                List<VideoMetadata> batch = batches.get(i);
                
                for (VideoMetadata video : batch) {
                    try {
                        VideoProcessingResult result = processVideo(
                            video,
                            collectionId,
                            request.tagStrategy(),
                            request.verbosity(),
                            existingTags,
                            eventEmitter
                        );
                        results.add(result);
                    } catch (Exception e) {
                        log.error("Failed to process video: " + video.videoId(), e);
                        eventEmitter.accept(ProgressEvent.error("Failed to process video: " + video.title()));
                        results.add(new VideoProcessingResult(
                            video,
                            List.of(),
                            ProcessingStatus.FAILED,
                            e.getMessage()
                        ));
                    }
                }

                eventEmitter.accept(ProgressEvent.batchCompleted(i + 1));
            }

            // Send completion summary
            ProcessingSummary summary = buildSummary(results);
            eventEmitter.accept(ProgressEvent.completed(summary));

        } catch (Exception e) {
            log.error("Fatal error processing playlist", e);
            eventEmitter.accept(ProgressEvent.error("Fatal error: " + e.getMessage()));
        }
    }

    private VideoProcessingResult processVideo(
            VideoMetadata video,
            Long collectionId,
            TagStrategy tagStrategy,
            Verbosity verbosity,
            List<RaindropTag> existingTags,
            Consumer<ProgressEvent> eventEmitter) {

        // Check for duplicates
        if (raindropService.bookmarkExists(collectionId, video.url())) {
            eventEmitter.accept(ProgressEvent.videoSkipped(video.title()));
            return new VideoProcessingResult(
                video,
                List.of(),
                ProcessingStatus.SKIPPED,
                "Already exists"
            );
        }

        // Generate tags
        List<TagWithConfidence> selectedTags = videoTaggingService.generateTags(
            video,
            existingTags,
            tagStrategy
        );

        // Save to Raindrop
        List<String> tagNames = selectedTags.stream()
                .map(TagWithConfidence::tag)
                .collect(Collectors.toList());

        raindropService.createBookmark(
            collectionId,
            video.url(),
            video.title(),
            tagNames
        );

        // Emit progress based on verbosity
        if (verbosity != Verbosity.MINIMAL) {
            eventEmitter.accept(ProgressEvent.videoCompleted(
                new VideoProcessingResult(video, selectedTags, ProcessingStatus.SUCCESS, null)
            ));
        }

        return new VideoProcessingResult(video, selectedTags, ProcessingStatus.SUCCESS, null);
    }

    private ProcessingSummary buildSummary(List<VideoProcessingResult> results) {
        int succeeded = (int) results.stream().filter(r -> r.status() == ProcessingStatus.SUCCESS).count();
        int skipped = (int) results.stream().filter(r -> r.status() == ProcessingStatus.SKIPPED).count();
        int failed = (int) results.stream().filter(r -> r.status() == ProcessingStatus.FAILED).count();

        return new ProcessingSummary(results.size(), succeeded, skipped, failed);
    }

    private <T> List<List<T>> partition(List<T> list, int batchSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            partitions.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return partitions;
    }
}
```

**Step 5: Enable async processing**

Modify: `src/main/java/org/bartram/vidtag/VidtagApplication.java`

```java
package org.bartram.vidtag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableCaching
@EnableAsync
public class VidtagApplication {

    public static void main(String[] args) {
        SpringApplication.run(VidtagApplication.class, args);
    }

}
```

**Step 6: Run test to verify it passes**

Run: `./gradlew test --tests 'org.bartram.vidtag.service.VideoTaggingOrchestratorTest'`
Expected: PASS

**Step 7: Run all tests**

Run: `./gradlew test`
Expected: All tests PASS

**Step 8: Commit orchestrator**

```bash
git add src/main/java/org/bartram/vidtag/service/VideoTaggingOrchestrator.java src/main/java/org/bartram/vidtag/event/ProgressEvent.java src/test/java/org/bartram/vidtag/service/VideoTaggingOrchestratorTest.java src/main/java/org/bartram/vidtag/VidtagApplication.java
git commit -m "feat: add video tagging orchestrator with batch processing"
```

---

## Task 6: REST Controller with SSE

**Files:**
- Create: `src/main/java/org/bartram/vidtag/controller/PlaylistTaggingController.java`
- Create: `src/test/java/org/bartram/vidtag/controller/PlaylistTaggingControllerTest.java`

**Step 1: Write test for controller endpoint**

Create: `src/test/java/org/bartram/vidtag/controller/PlaylistTaggingControllerTest.java`

```java
package org.bartram.vidtag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bartram.vidtag.dto.TagPlaylistRequest;
import org.bartram.vidtag.model.TagStrategy;
import org.bartram.vidtag.model.Verbosity;
import org.bartram.vidtag.model.VideoFilters;
import org.bartram.vidtag.service.VideoTaggingOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlaylistTaggingController.class)
class PlaylistTaggingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VideoTaggingOrchestrator orchestrator;

    @Test
    void shouldAcceptTagPlaylistRequest() throws Exception {
        var request = new TagPlaylistRequest(
            "PLxxx",
            "My Videos",
            new VideoFilters(null, null, 10),
            new TagStrategy(3, 0.7, null),
            Verbosity.STANDARD
        );

        mockMvc.perform(post("/api/v1/playlists/tag")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(orchestrator).processPlaylist(any(), any());
    }

    @Test
    void shouldRejectInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/playlists/tag")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'org.bartram.vidtag.controller.PlaylistTaggingControllerTest'`
Expected: FAIL (Controller doesn't exist)

**Step 3: Create PlaylistTaggingController**

Create: `src/main/java/org/bartram/vidtag/controller/PlaylistTaggingController.java`

```java
package org.bartram.vidtag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bartram.vidtag.dto.TagPlaylistRequest;
import org.bartram.vidtag.event.ProgressEvent;
import org.bartram.vidtag.service.VideoTaggingOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/playlists")
public class PlaylistTaggingController {

    private static final Logger log = LoggerFactory.getLogger(PlaylistTaggingController.class);
    private static final long SSE_TIMEOUT = 3600000L; // 1 hour

    private final VideoTaggingOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

    public PlaylistTaggingController(
            VideoTaggingOrchestrator orchestrator,
            ObjectMapper objectMapper) {
        this.orchestrator = orchestrator;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/tag", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> tagPlaylist(@Validated @RequestBody TagPlaylistRequest request) {
        
        // Validate request
        if (request.playlistInput() == null || request.playlistInput().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (request.raindropCollectionTitle() == null || request.raindropCollectionTitle().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        // Handle completion and errors
        emitter.onCompletion(() -> log.info("SSE completed for playlist: {}", request.playlistInput()));
        emitter.onTimeout(() -> {
            log.warn("SSE timeout for playlist: {}", request.playlistInput());
            emitter.complete();
        });
        emitter.onError(e -> {
            log.error("SSE error for playlist: {}", request.playlistInput(), e);
            emitter.completeWithError(e);
        });

        // Start async processing
        orchestrator.processPlaylist(request, event -> sendEvent(emitter, event));

        return ResponseEntity.ok(emitter);
    }

    private void sendEvent(SseEmitter emitter, ProgressEvent event) {
        try {
            String eventData = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event()
                    .name(event.eventType())
                    .data(eventData));

            // Complete emitter on completion or fatal error
            if ("completed".equals(event.eventType()) || 
                ("error".equals(event.eventType()) && event.message().startsWith("Fatal"))) {
                emitter.complete();
            }
        } catch (IOException e) {
            log.error("Failed to send SSE event", e);
            emitter.completeWithError(e);
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests 'org.bartram.vidtag.controller.PlaylistTaggingControllerTest'`
Expected: PASS

**Step 5: Run all tests**

Run: `./gradlew test`
Expected: All tests PASS

**Step 6: Commit controller**

```bash
git add src/main/java/org/bartram/vidtag/controller/PlaylistTaggingController.java src/test/java/org/bartram/vidtag/controller/PlaylistTaggingControllerTest.java
git commit -m "feat: add REST controller with SSE support"
```

---

## Task 7: Integration Test with Testcontainers

**Files:**
- Create: `src/test/java/org/bartram/vidtag/VideoTaggingIntegrationTest.java`
- Create: `src/test/java/org/bartram/vidtag/client/MockYouTubeApiClient.java`
- Create: `src/test/java/org/bartram/vidtag/client/MockRaindropApiClient.java`

**Step 1: Create mock YouTube client**

Create: `src/test/java/org/bartram/vidtag/client/MockYouTubeApiClient.java`

```java
package org.bartram.vidtag.client;

import org.bartram.vidtag.model.VideoMetadata;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.util.List;

@TestConfiguration
public class MockYouTubeApiClient {

    @Bean
    @Primary
    public YouTubeApiClient youTubeApiClient() {
        return playlistId -> List.of(
            new VideoMetadata(
                "video1",
                "https://youtube.com/watch?v=video1",
                "Test Video 1",
                "Description 1",
                Instant.parse("2024-01-15T10:00:00Z"),
                300
            ),
            new VideoMetadata(
                "video2",
                "https://youtube.com/watch?v=video2",
                "Test Video 2",
                "Description 2",
                Instant.parse("2024-01-16T10:00:00Z"),
                600
            )
        );
    }
}
```

**Step 2: Create mock Raindrop client**

Create: `src/test/java/org/bartram/vidtag/client/MockRaindropApiClient.java`

```java
package org.bartram.vidtag.client;

import org.bartram.vidtag.model.RaindropCollection;
import org.bartram.vidtag.model.RaindropTag;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@TestConfiguration
public class MockRaindropApiClient {

    private final Set<String> savedUrls = new HashSet<>();

    @Bean
    @Primary
    public RaindropApiClient raindropApiClient() {
        return new RaindropApiClient() {
            @Override
            public List<RaindropTag> getUserTags(String userId) {
                return List.of(
                    new RaindropTag("java"),
                    new RaindropTag("spring"),
                    new RaindropTag("tutorial"),
                    new RaindropTag("programming")
                );
            }

            @Override
            public List<RaindropCollection> getUserCollections(String userId) {
                return List.of(
                    new RaindropCollection(123L, "My Videos"),
                    new RaindropCollection(456L, "Work")
                );
            }

            @Override
            public boolean bookmarkExists(Long collectionId, String url) {
                return savedUrls.contains(url);
            }

            @Override
            public void createBookmark(Long collectionId, String url, String title, List<String> tags) {
                savedUrls.add(url);
            }
        };
    }
}
```

**Step 3: Write integration test**

Create: `src/test/java/org/bartram/vidtag/VideoTaggingIntegrationTest.java`

```java
package org.bartram.vidtag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bartram.vidtag.client.MockRaindropApiClient;
import org.bartram.vidtag.client.MockYouTubeApiClient;
import org.bartram.vidtag.model.RaindropCollection;
import org.bartram.vidtag.dto.TagPlaylistRequest;
import org.bartram.vidtag.model.TagStrategy;
import org.bartram.vidtag.model.Verbosity;
import org.bartram.vidtag.model.VideoFilters;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import({MockYouTubeApiClient.class, MockRaindropApiClient.class})
@Disabled("Integration test - requires API key for Claude AI")
class VideoTaggingIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldProcessPlaylistEndToEnd() throws Exception {
        var request = new TagPlaylistRequest(
            "PLxxx123",
            "My Videos",
            new VideoFilters(null, null, 10),
            new TagStrategy(3, 0.7, "Focus on programming"),
            Verbosity.DETAILED
        );

        mockMvc.perform(post("/api/v1/playlists/tag")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Note: SSE response would need async verification in real test
    }
}
```

**Step 4: Run test**

Run: `./gradlew test --tests 'org.bartram.vidtag.VideoTaggingIntegrationTest'`
Expected: Test is disabled, skipped

**Step 5: Run all tests**

Run: `./gradlew test`
Expected: All tests PASS

**Step 6: Commit integration test**

```bash
git add src/test/java/org/bartram/vidtag/VideoTaggingIntegrationTest.java src/test/java/org/bartram/vidtag/client/
git commit -m "test: add integration test with Testcontainers"
```

---

## Task 8: Final Build and Documentation

**Files:**
- Modify: `CLAUDE.md` (update with implementation details)

**Step 1: Run full build**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL

**Step 2: Update CLAUDE.md with implementation notes**

Add to `CLAUDE.md`:

```markdown
## Implementation Status

### Completed Features
- ✅ Data models and DTOs for video tagging workflow
- ✅ YouTube service with circuit breaker and filtering
- ✅ Raindrop service with Redis caching (15min TTL)
- ✅ Video tagging service with Spring AI (Claude integration)
- ✅ Video tagging orchestrator with batch processing (10 videos/batch)
- ✅ REST controller with SSE streaming support
- ✅ Integration tests with Testcontainers

### API Endpoint
```
POST /api/v1/playlists/tag
Content-Type: application/json
Accept: text/event-stream
```

### Testing
Integration tests are disabled by default (require API key):
- Enable by removing `@Disabled` annotation
- Set environment variable: `API_KEY_VIDTAG=<your-key>`
- Run: `./gradlew test`

### Circuit Breaker Configuration
All external APIs (YouTube, Raindrop, Claude) have circuit breakers configured in `application.yaml`:
- Failure threshold: 50%
- Wait duration: 30s
- Retry attempts: 2-3 with exponential backoff

### Cache Configuration
Raindrop tags are cached in Redis with 15-minute TTL to reduce API calls.
```

**Step 3: Verify all tests pass**

Run: `./gradlew test`
Expected: All tests PASS

**Step 4: Final commit**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md with implementation status"
```

**Step 5: Build verification**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

---

## Next Steps

After completing this implementation:

1. **API Client Implementations**: Implement actual `YouTubeApiClient` and `RaindropApiClient` using real API clients
2. **Authentication**: Add user authentication and extract user ID from security context
3. **Error Response DTOs**: Create proper error response models for validation failures
4. **API Documentation**: Add OpenAPI/Swagger documentation
5. **Monitoring**: Configure Spring Boot Actuator endpoints for circuit breaker metrics
6. **Manual Testing**: Test with real YouTube playlists and Raindrop account
7. **Rate Limiting**: Add rate limiting to prevent API quota exhaustion
