# Scheduled Playlist Processor and Dockerization Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add scheduled YouTube playlist processing job and containerize the application with Docker.

**Architecture:** Spring `@Scheduled` job with `fixedDelay` to process a configurable YouTube playlist hourly, using existing `VideoTaggingOrchestrator`. Multi-stage Dockerfile with JDK21 build and JRE21-alpine runtime. Docker Compose orchestration for local testing with app + Redis containers.

**Tech Stack:** Spring Boot 4.0.2, Java 21, Spring Scheduling, Docker, Docker Compose, Testcontainers

---

## Task 1: Add Configuration Properties

**Files:**
- Create: `src/main/java/org/bartram/vidtag/config/SchedulerProperties.java`
- Modify: `src/main/resources/application.yaml:31-34`

**Step 1: Write the failing test**

Create test file:

```java
package org.bartram.vidtag.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
    "vidtag.scheduler.enabled=true",
    "vidtag.scheduler.fixed-delay-hours=1",
    "vidtag.scheduler.playlist-name=tag"
})
class SchedulerPropertiesTest {

    @Autowired
    private SchedulerProperties schedulerProperties;

    @Test
    void shouldLoadSchedulerProperties() {
        assertThat(schedulerProperties).isNotNull();
        assertThat(schedulerProperties.isEnabled()).isTrue();
        assertThat(schedulerProperties.getFixedDelayHours()).isEqualTo(1);
        assertThat(schedulerProperties.getPlaylistName()).isEqualTo("tag");
    }

    @Test
    void shouldHaveDefaultValues() {
        // This will use application.yaml defaults
        assertThat(schedulerProperties.getPlaylistName()).isNotNull();
        assertThat(schedulerProperties.getFixedDelayHours()).isPositive();
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'org.bartram.vidtag.config.SchedulerPropertiesTest'`
Expected: FAIL with "No bean named 'schedulerProperties'"

**Step 3: Write minimal implementation**

Create configuration properties class:

```java
package org.bartram.vidtag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the playlist processing scheduler.
 */
@Component
@ConfigurationProperties(prefix = "vidtag.scheduler")
public class SchedulerProperties {

    /**
     * Enable or disable the scheduled playlist processor.
     */
    private boolean enabled = true;

    /**
     * Fixed delay between job executions in hours.
     */
    private int fixedDelayHours = 1;

    /**
     * Name of the YouTube playlist to process.
     */
    private String playlistName = "tag";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getFixedDelayHours() {
        return fixedDelayHours;
    }

    public void setFixedDelayHours(int fixedDelayHours) {
        this.fixedDelayHours = fixedDelayHours;
    }

    public String getPlaylistName() {
        return playlistName;
    }

    public void setPlaylistName(String playlistName) {
        this.playlistName = playlistName;
    }
}
```

**Step 4: Add configuration to application.yaml**

Add to `src/main/resources/application.yaml` after line 33 (in vidtag section):

```yaml
  # Scheduler Configuration
  scheduler:
    enabled: true  # Enable/disable scheduled playlist processing
    fixed-delay-hours: 1  # Delay between job executions in hours
    playlist-name: "tag"  # YouTube playlist name to process
```

**Step 5: Run test to verify it passes**

Run: `./gradlew test --tests 'org.bartram.vidtag.config.SchedulerPropertiesTest'`
Expected: PASS

**Step 6: Commit**

```bash
git add src/main/java/org/bartram/vidtag/config/SchedulerProperties.java \
  src/test/java/org/bartram/vidtag/config/SchedulerPropertiesTest.java \
  src/main/resources/application.yaml
git commit -m "feat: add scheduler configuration properties

- Add SchedulerProperties for playlist processor config
- Default: enabled, 1 hour delay, playlist name 'tag'
- Make all settings configurable via application.yaml"
```

---

## Task 2: Add Playlist Search to YouTubeService

**Files:**
- Modify: `src/main/java/org/bartram/vidtag/service/YouTubeService.java:107`
- Modify: `src/main/java/org/bartram/vidtag/client/YouTubeApiClient.java`
- Create: `src/test/java/org/bartram/vidtag/service/YouTubeServicePlaylistSearchTest.java`

**Step 1: Write the failing test**

Create test file:

```java
package org.bartram.vidtag.service;

import org.bartram.vidtag.client.YouTubeApiClient;
import org.bartram.vidtag.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YouTubeServicePlaylistSearchTest {

    @Mock
    private YouTubeApiClient youtubeApiClient;

    @InjectMocks
    private YouTubeService youtubeService;

    @Test
    void shouldFindPlaylistIdByName() {
        when(youtubeApiClient.findPlaylistByName("tag")).thenReturn("PLxyz123");

        String playlistId = youtubeService.findPlaylistByName("tag");

        assertThat(playlistId).isEqualTo("PLxyz123");
    }

    @Test
    void shouldThrowExceptionWhenPlaylistNotFound() {
        when(youtubeApiClient.findPlaylistByName("nonexistent")).thenReturn(null);

        assertThatThrownBy(() -> youtubeService.findPlaylistByName("nonexistent"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Playlist not found: nonexistent");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'org.bartram.vidtag.service.YouTubeServicePlaylistSearchTest'`
Expected: FAIL with "Method 'findPlaylistByName' does not exist"

**Step 3: Add method to YouTubeApiClient interface**

Add to `src/main/java/org/bartram/vidtag/client/YouTubeApiClient.java`:

```java
/**
 * Finds a playlist by its name.
 *
 * @param playlistName the name of the playlist to find
 * @return the playlist ID if found, null otherwise
 */
String findPlaylistByName(String playlistName);
```

**Step 4: Implement method in YouTubeService**

Add to `src/main/java/org/bartram/vidtag/service/YouTubeService.java` after line 107:

```java
/**
 * Finds a playlist ID by searching for the playlist name.
 * Protected by circuit breaker and retry logic.
 *
 * @param playlistName the name of the playlist to search for
 * @return the playlist ID
 * @throws ResourceNotFoundException if playlist not found
 */
@Retry(name = "youtube")
@CircuitBreaker(name = "youtube", fallbackMethod = "findPlaylistByNameFallback")
public String findPlaylistByName(String playlistName) {
    log.debug("Searching for playlist with name: {}", playlistName);

    String playlistId = youtubeApiClient.findPlaylistByName(playlistName);

    if (playlistId == null) {
        log.warn("Playlist not found: {}", playlistName);
        throw new ResourceNotFoundException("Playlist not found: " + playlistName);
    }

    log.info("Found playlist '{}' with ID: {}", playlistName, playlistId);
    return playlistId;
}

/**
 * Fallback method when YouTube API circuit breaker is open during playlist search.
 */
private String findPlaylistByNameFallback(String playlistName, Throwable throwable) {
    log.error("YouTube API circuit breaker fallback triggered for playlist search '{}': {}",
        playlistName, throwable.getMessage());
    throw new ExternalServiceException("youtube", "YouTube API is currently unavailable", throwable);
}
```

**Step 5: Add import statements**

Add to imports in `src/main/java/org/bartram/vidtag/service/YouTubeService.java`:

```java
import org.bartram.vidtag.exception.ResourceNotFoundException;
```

**Step 6: Run test to verify it passes**

Run: `./gradlew test --tests 'org.bartram.vidtag.service.YouTubeServicePlaylistSearchTest'`
Expected: PASS

**Step 7: Commit**

```bash
git add src/main/java/org/bartram/vidtag/client/YouTubeApiClient.java \
  src/main/java/org/bartram/vidtag/service/YouTubeService.java \
  src/test/java/org/bartram/vidtag/service/YouTubeServicePlaylistSearchTest.java
git commit -m "feat: add playlist search by name to YouTubeService

- Add findPlaylistByName method with circuit breaker
- Throw ResourceNotFoundException when not found
- Add test coverage for success and not found cases"
```

---

## Task 3: Implement Playlist Search in YouTubeApiClientImpl

**Files:**
- Modify: `src/main/java/org/bartram/vidtag/client/impl/YouTubeApiClientImpl.java`
- Create: `src/test/java/org/bartram/vidtag/client/impl/YouTubeApiClientImplSearchTest.java`

**Step 1: Write the failing test**

Create test file:

```java
package org.bartram.vidtag.client.impl;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistListResponse;
import org.bartram.vidtag.exception.ExternalServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YouTubeApiClientImplSearchTest {

    @Mock
    private YouTube youtube;

    @Mock
    private YouTube.Playlists playlists;

    @Mock
    private YouTube.Playlists.List listRequest;

    private YouTubeApiClientImpl client;

    @BeforeEach
    void setUp() {
        client = new YouTubeApiClientImpl(youtube, "test-api-key");
    }

    @Test
    void shouldFindPlaylistByName() throws IOException {
        Playlist playlist = new Playlist();
        playlist.setId("PLxyz123");
        playlist.setSnippet(new com.google.api.services.youtube.model.PlaylistSnippet().setTitle("tag"));

        PlaylistListResponse response = new PlaylistListResponse();
        response.setItems(List.of(playlist));

        when(youtube.playlists()).thenReturn(playlists);
        when(playlists.list(any())).thenReturn(listRequest);
        when(listRequest.setMine(true)).thenReturn(listRequest);
        when(listRequest.setMaxResults(50L)).thenReturn(listRequest);
        when(listRequest.execute()).thenReturn(response);

        String playlistId = client.findPlaylistByName("tag");

        assertThat(playlistId).isEqualTo("PLxyz123");
    }

    @Test
    void shouldReturnNullWhenPlaylistNotFound() throws IOException {
        PlaylistListResponse response = new PlaylistListResponse();
        response.setItems(Collections.emptyList());

        when(youtube.playlists()).thenReturn(playlists);
        when(playlists.list(any())).thenReturn(listRequest);
        when(listRequest.setMine(true)).thenReturn(listRequest);
        when(listRequest.setMaxResults(50L)).thenReturn(listRequest);
        when(listRequest.execute()).thenReturn(response);

        String playlistId = client.findPlaylistByName("nonexistent");

        assertThat(playlistId).isNull();
    }

    @Test
    void shouldThrowExceptionOnApiError() throws IOException {
        when(youtube.playlists()).thenReturn(playlists);
        when(playlists.list(any())).thenReturn(listRequest);
        when(listRequest.setMine(true)).thenReturn(listRequest);
        when(listRequest.setMaxResults(50L)).thenReturn(listRequest);
        when(listRequest.execute()).thenThrow(new IOException("API error"));

        assertThatThrownBy(() -> client.findPlaylistByName("tag"))
            .isInstanceOf(ExternalServiceException.class)
            .hasMessageContaining("Failed to search for playlist");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'org.bartram.vidtag.client.impl.YouTubeApiClientImplSearchTest'`
Expected: FAIL with "Method 'findPlaylistByName' does not exist"

**Step 3: Implement findPlaylistByName method**

Add to `src/main/java/org/bartram/vidtag/client/impl/YouTubeApiClientImpl.java`:

```java
@Override
public String findPlaylistByName(String playlistName) {
    try {
        log.debug("Searching for playlist with name: {}", playlistName);

        YouTube.Playlists.List request = youtube.playlists()
            .list(List.of("snippet"))
            .setMine(true)
            .setMaxResults(50L);

        PlaylistListResponse response = request.execute();
        List<Playlist> playlists = response.getItems();

        if (playlists == null || playlists.isEmpty()) {
            log.debug("No playlists found");
            return null;
        }

        // Search for playlist with matching title (case-insensitive)
        for (Playlist playlist : playlists) {
            String title = playlist.getSnippet().getTitle();
            if (title != null && title.equalsIgnoreCase(playlistName)) {
                log.debug("Found playlist '{}' with ID: {}", title, playlist.getId());
                return playlist.getId();
            }
        }

        log.debug("Playlist '{}' not found in {} playlists", playlistName, playlists.size());
        return null;

    } catch (IOException e) {
        log.error("Failed to search for playlist '{}': {}", playlistName, e.getMessage(), e);
        throw new ExternalServiceException("youtube",
            String.format("Failed to search for playlist '%s': %s", playlistName, e.getMessage()), e);
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests 'org.bartram.vidtag.client.impl.YouTubeApiClientImplSearchTest'`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/org/bartram/vidtag/client/impl/YouTubeApiClientImpl.java \
  src/test/java/org/bartram/vidtag/client/impl/YouTubeApiClientImplSearchTest.java
git commit -m "feat: implement playlist search in YouTubeApiClientImpl

- Search user's playlists by name (case-insensitive)
- Return null when playlist not found
- Add comprehensive test coverage"
```

---

## Task 4: Add Stub Implementation for Playlist Search

**Files:**
- Modify: `src/test/java/org/bartram/vidtag/TestcontainersConfiguration.java`

**Step 1: Find and update stub implementation**

Read the TestcontainersConfiguration to find the stub:

Run: `grep -n "YouTubeApiClient" src/test/java/org/bartram/vidtag/TestcontainersConfiguration.java`

**Step 2: Add findPlaylistByName to stub**

Add method to the stub `YouTubeApiClient` bean in `TestcontainersConfiguration.java`:

```java
@Override
public String findPlaylistByName(String playlistName) {
    return null;  // Stub returns null (playlist not found)
}
```

**Step 3: Run all tests to verify stubs work**

Run: `./gradlew test`
Expected: PASS (all tests should still pass with stub)

**Step 4: Commit**

```bash
git add src/test/java/org/bartram/vidtag/TestcontainersConfiguration.java
git commit -m "feat: add findPlaylistByName to YouTubeApiClient stub

- Return null for stub implementation
- Allows tests to run without YouTube API credentials"
```

---

## Task 5: Create Scheduled Playlist Processor Service

**Files:**
- Create: `src/main/java/org/bartram/vidtag/service/PlaylistProcessingScheduler.java`
- Create: `src/test/java/org/bartram/vidtag/service/PlaylistProcessingSchedulerTest.java`

**Step 1: Write the failing test**

Create test file:

```java
package org.bartram.vidtag.service;

import org.bartram.vidtag.config.SchedulerProperties;
import org.bartram.vidtag.dto.TagPlaylistRequest;
import org.bartram.vidtag.model.TagStrategy;
import org.bartram.vidtag.model.VideoFilters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaylistProcessingSchedulerTest {

    @Mock
    private YouTubeService youtubeService;

    @Mock
    private VideoTaggingOrchestrator orchestrator;

    @Mock
    private SchedulerProperties schedulerProperties;

    @Captor
    private ArgumentCaptor<TagPlaylistRequest> requestCaptor;

    private PlaylistProcessingScheduler scheduler;

    @BeforeEach
    void setUp() {
        when(schedulerProperties.isEnabled()).thenReturn(true);
        when(schedulerProperties.getPlaylistName()).thenReturn("tag");

        scheduler = new PlaylistProcessingScheduler(
            youtubeService,
            orchestrator,
            schedulerProperties
        );
    }

    @Test
    void shouldProcessPlaylistWhenEnabled() {
        when(youtubeService.findPlaylistByName("tag")).thenReturn("PLxyz123");

        scheduler.processTagPlaylist();

        verify(youtubeService).findPlaylistByName("tag");
        verify(orchestrator).processPlaylist(requestCaptor.capture(), any());

        TagPlaylistRequest request = requestCaptor.getValue();
        assertThat(request.playlistInput()).isEqualTo("PLxyz123");
        assertThat(request.raindropCollectionTitle()).isEqualTo("Videos");
        assertThat(request.tagStrategy()).isEqualTo(TagStrategy.SUGGEST);
    }

    @Test
    void shouldSkipProcessingWhenDisabled() {
        when(schedulerProperties.isEnabled()).thenReturn(false);

        scheduler.processTagPlaylist();

        verify(youtubeService, never()).findPlaylistByName(any());
        verify(orchestrator, never()).processPlaylist(any(), any());
    }

    @Test
    void shouldContinueOnException() {
        when(youtubeService.findPlaylistByName("tag"))
            .thenThrow(new RuntimeException("API error"));

        // Should not throw exception
        scheduler.processTagPlaylist();

        verify(youtubeService).findPlaylistByName("tag");
        verify(orchestrator, never()).processPlaylist(any(), any());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'org.bartram.vidtag.service.PlaylistProcessingSchedulerTest'`
Expected: FAIL with "Class 'PlaylistProcessingScheduler' does not exist"

**Step 3: Write minimal implementation**

Create scheduler class:

```java
package org.bartram.vidtag.service;

import org.bartram.vidtag.config.SchedulerProperties;
import org.bartram.vidtag.dto.TagPlaylistRequest;
import org.bartram.vidtag.model.TagStrategy;
import org.bartram.vidtag.model.VideoFilters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Scheduled service that processes a YouTube playlist at fixed intervals.
 * Searches for a playlist by name, then processes all videos through the tagging workflow.
 */
@Service
@EnableScheduling
@ConditionalOnProperty(prefix = "vidtag.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PlaylistProcessingScheduler {

    private static final Logger log = LoggerFactory.getLogger(PlaylistProcessingScheduler.class);
    private static final String DEFAULT_COLLECTION = "Videos";

    private final YouTubeService youtubeService;
    private final VideoTaggingOrchestrator orchestrator;
    private final SchedulerProperties schedulerProperties;

    public PlaylistProcessingScheduler(
            YouTubeService youtubeService,
            VideoTaggingOrchestrator orchestrator,
            SchedulerProperties schedulerProperties) {
        this.youtubeService = youtubeService;
        this.orchestrator = orchestrator;
        this.schedulerProperties = schedulerProperties;
    }

    /**
     * Scheduled job that processes the configured YouTube playlist.
     * Runs with fixed delay (configured in hours) to prevent overlapping executions.
     * Errors are logged but do not stop future executions.
     */
    @Scheduled(
        fixedDelayString = "#{${vidtag.scheduler.fixed-delay-hours} * 60 * 60 * 1000}",
        initialDelayString = "#{10 * 1000}"  // 10 second initial delay
    )
    public void processTagPlaylist() {
        if (!schedulerProperties.isEnabled()) {
            log.debug("Scheduler is disabled, skipping execution");
            return;
        }

        String playlistName = schedulerProperties.getPlaylistName();
        log.info("Starting scheduled playlist processing for: {}", playlistName);

        try {
            // Find playlist by name
            String playlistId = youtubeService.findPlaylistByName(playlistName);
            log.debug("Found playlist '{}' with ID: {}", playlistName, playlistId);

            // Create request with default settings
            TagPlaylistRequest request = new TagPlaylistRequest(
                playlistId,
                DEFAULT_COLLECTION,
                TagStrategy.SUGGEST,
                new VideoFilters(null, null, null)
            );

            // Process playlist asynchronously (orchestrator handles SSE events)
            orchestrator.processPlaylist(request, event -> {
                // Log progress events (no SSE client for scheduled job)
                log.debug("Progress: {}", event.message());
            });

            log.info("Scheduled playlist processing initiated for: {}", playlistName);

        } catch (Exception e) {
            log.error("Failed to process scheduled playlist '{}': {}",
                playlistName, e.getMessage(), e);
            // Don't rethrow - allow scheduler to continue
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests 'org.bartram.vidtag.service.PlaylistProcessingSchedulerTest'`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/org/bartram/vidtag/service/PlaylistProcessingScheduler.java \
  src/test/java/org/bartram/vidtag/service/PlaylistProcessingSchedulerTest.java
git commit -m "feat: add scheduled playlist processor

- Process configured playlist with fixed delay
- Default 1 hour delay, 10 second initial delay
- Skip processing when disabled
- Log errors but continue scheduling"
```

---

## Task 6: Create Dockerfile

**Files:**
- Create: `Dockerfile`
- Create: `.dockerignore`

**Step 1: Create .dockerignore file**

Create `.dockerignore` in project root:

```
# Gradle
.gradle/
build/
gradle-app.setting

# IDE
.idea/
.vscode/
*.iml
*.ipr
*.iws

# Git
.git/
.gitignore

# Documentation
docs/
*.md
!CLAUDE.md

# Test data
src/test/

# Local settings
.claude/
compose.yaml
```

**Step 2: Create multi-stage Dockerfile**

Create `Dockerfile` in project root:

```dockerfile
# Stage 1: Build the application
FROM gradle:8-jdk21-alpine AS build

# Set working directory
WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradle gradle
COPY gradlew .
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src src

# Build the application (skip tests for faster builds)
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: Runtime image
FROM eclipse-temurin:21-jre-alpine

# Create app user for security
RUN addgroup -S spring && adduser -S spring -G spring

# Set working directory
WORKDIR /app

# Copy the built JAR from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Change ownership to app user
RUN chown -R spring:spring /app

# Switch to app user
USER spring

# Expose port 8080
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Step 3: Test Docker build**

Run: `docker build -t vidtag:test .`
Expected: SUCCESS - image builds without errors

**Step 4: Test Docker run (should fail without env vars)**

Run: `docker run --rm -p 8080:8080 vidtag:test`
Expected: Container starts but may have errors about missing API keys (this is expected)
Stop with Ctrl+C

**Step 5: Commit**

```bash
git add Dockerfile .dockerignore
git commit -m "feat: add multi-stage Dockerfile

- Build stage with Gradle 8 and JDK 21
- Runtime stage with JRE 21 Alpine for smaller image
- Non-root user for security
- Health check via actuator endpoint
- .dockerignore to exclude unnecessary files"
```

---

## Task 7: Update Docker Compose for Full Stack

**Files:**
- Modify: `compose.yaml`
- Create: `.env.example`

**Step 1: Create example env file**

Create `.env.example` in project root:

```
# Anthropic API Key (required)
VIDTAG_CLAUDE_API_KEY=your-claude-api-key-here

# YouTube API Key (optional - uses stub if not provided)
YOUTUBE_API_KEY=your-youtube-api-key-here

# Raindrop.io API Token (optional - uses stub if not provided)
RAINDROP_API_TOKEN=your-raindrop-token-here

# Debug Mode (optional)
DEBUG_MODE=false
```

**Step 2: Update compose.yaml**

Replace contents of `compose.yaml`:

```yaml
services:
  redis:
    image: redis:7-alpine
    container_name: vidtag-redis
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 3
    networks:
      - vidtag-network

  app:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: vidtag-app
    ports:
      - "8080:8080"
    environment:
      # Spring Redis connection uses service name
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: 6379

      # API Keys from .env file
      VIDTAG_CLAUDE_API_KEY: ${VIDTAG_CLAUDE_API_KEY}
      YOUTUBE_API_KEY: ${YOUTUBE_API_KEY:-}
      RAINDROP_API_TOKEN: ${RAINDROP_API_TOKEN:-}

      # Optional settings
      DEBUG_MODE: ${DEBUG_MODE:-false}

    depends_on:
      redis:
        condition: service_healthy
    networks:
      - vidtag-network
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 3s
      start_period: 40s
      retries: 3

networks:
  vidtag-network:
    driver: bridge
```

**Step 3: Test compose file syntax**

Run: `docker compose config`
Expected: YAML is valid and outputs the merged configuration

**Step 4: Add .env to .gitignore**

Run: `echo ".env" >> .gitignore`

**Step 5: Commit**

```bash
git add compose.yaml .env.example .gitignore
git commit -m "feat: update Docker Compose for full stack

- Add vidtag app service with build configuration
- Configure Redis connection via service name
- Use .env file for API keys (not committed)
- Add health checks and service dependencies
- Provide .env.example for reference"
```

---

## Task 8: Add Docker Testing Documentation

**Files:**
- Create: `docs/DOCKER.md`

**Step 1: Create Docker documentation**

Create `docs/DOCKER.md`:

```markdown
# Docker Deployment Guide

This guide covers running VidTag in Docker for local development and testing.

## Prerequisites

- Docker Desktop installed and running
- Docker Compose V2 (included with Docker Desktop)
- API keys (at minimum: `VIDTAG_CLAUDE_API_KEY`)

## Quick Start

### 1. Configure Environment Variables

Copy the example environment file:

\`\`\`bash
cp .env.example .env
\`\`\`

Edit `.env` and add your API keys:

\`\`\`bash
VIDTAG_CLAUDE_API_KEY=your-actual-claude-api-key
YOUTUBE_API_KEY=your-youtube-key  # Optional
RAINDROP_API_TOKEN=your-raindrop-token  # Optional
\`\`\`

**Note:** If YouTube or Raindrop keys are not provided, stub implementations will be used.

### 2. Build and Start Services

Build the application image and start all services:

\`\`\`bash
docker compose up --build
\`\`\`

This will:
- Build the VidTag application image (multi-stage build)
- Start Redis container
- Start VidTag application container
- Configure networking between containers

### 3. Verify Services

Check that both services are healthy:

\`\`\`bash
docker compose ps
\`\`\`

Expected output:
\`\`\`
NAME          IMAGE          STATUS         PORTS
vidtag-app    vidtag-app     Up (healthy)   0.0.0.0:8080->8080/tcp
vidtag-redis  redis:7-alpine Up (healthy)   0.0.0.0:6379->6379/tcp
\`\`\`

### 4. Test the Application

Access the Swagger UI:

\`\`\`
http://localhost:8080/swagger-ui.html
\`\`\`

Check application health:

\`\`\`bash
curl http://localhost:8080/actuator/health
\`\`\`

### 5. View Logs

View application logs:

\`\`\`bash
docker compose logs app -f
\`\`\`

View Redis logs:

\`\`\`bash
docker compose logs redis -f
\`\`\`

### 6. Stop Services

Stop and remove containers:

\`\`\`bash
docker compose down
\`\`\`

Keep volumes (preserves Redis data):

\`\`\`bash
docker compose down --volumes
\`\`\`

## Manual Testing

### Test Scheduled Job

The scheduled playlist processor runs automatically every hour. To test immediately:

1. Check logs for scheduler initialization:
\`\`\`bash
docker compose logs app | grep "Scheduler"
\`\`\`

2. Wait 10 seconds (initial delay) and watch for first execution:
\`\`\`bash
docker compose logs app -f | grep "playlist processing"
\`\`\`

### Test REST API

Use the existing manual test script with Docker:

\`\`\`bash
# From project root
bash scripts/manual-test.sh
\`\`\`

This will:
- Hit the `/api/v1/playlists/tag` endpoint
- Stream SSE events
- Show real-time progress

## Configuration

### Environment Variables

All Spring Boot properties can be overridden via environment variables in `compose.yaml`:

\`\`\`yaml
environment:
  SPRING_DATA_REDIS_HOST: redis
  VIDTAG_SCHEDULER_ENABLED: "true"
  VIDTAG_SCHEDULER_FIXED_DELAY_HOURS: "1"
  VIDTAG_SCHEDULER_PLAYLIST_NAME: "tag"
\`\`\`

### Port Mapping

Change ports in `compose.yaml`:

\`\`\`yaml
services:
  app:
    ports:
      - "9090:8080"  # Access app on localhost:9090
\`\`\`

## Troubleshooting

### Container Won't Start

Check logs:
\`\`\`bash
docker compose logs app
\`\`\`

Common issues:
- Missing `VIDTAG_CLAUDE_API_KEY` in `.env`
- Port 8080 already in use
- Redis not healthy

### API Keys Not Working

Verify environment variables are set:
\`\`\`bash
docker compose exec app env | grep API_KEY
\`\`\`

### Redis Connection Issues

Test Redis connectivity:
\`\`\`bash
docker compose exec app sh -c 'wget -O- redis:6379'
\`\`\`

### Rebuild After Code Changes

Force rebuild:
\`\`\`bash
docker compose up --build --force-recreate
\`\`\`

## Production Considerations

**DO NOT use this Docker Compose setup in production as-is.** Consider:

- Use secrets management (Docker Secrets, Kubernetes Secrets)
- Use external Redis (AWS ElastiCache, Redis Cloud)
- Configure logging aggregation
- Set resource limits
- Use container registry for images
- Enable TLS/HTTPS
- Configure backup strategy
\`\`\`

**Step 2: Commit**

\`\`\`bash
git add docs/DOCKER.md
git commit -m "docs: add Docker deployment guide

- Quick start instructions
- Environment configuration
- Testing procedures
- Troubleshooting tips
- Production warnings"
\`\`\`

---

## Task 9: Integration Test for Docker

**Files:**
- Create: `src/test/java/org/bartram/vidtag/DockerIntegrationTest.java`

**Step 1: Write Docker-based integration test**

Create test file:

\`\`\`java
package org.bartram.vidtag;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that verifies the application works correctly with Redis in Docker.
 * Uses Testcontainers to spin up Redis automatically.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DockerIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldStartSuccessfullyWithRedis() {
        assertThat(redis.isRunning()).isTrue();
    }

    @Test
    void shouldRespondToHealthCheck() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void shouldHaveRedisConnectionInHealth() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getBody()).contains("redis");
    }
}
\`\`\`

**Step 2: Run test to verify it passes**

Run: `./gradlew test --tests 'org.bartram.vidtag.DockerIntegrationTest'`
Expected: PASS (Testcontainers downloads Redis and runs test)

**Step 3: Commit**

\`\`\`bash
git add src/test/java/org/bartram/vidtag/DockerIntegrationTest.java
git commit -m "test: add Docker integration test

- Verify app starts with Redis in Docker
- Use Testcontainers for Redis
- Test health endpoint includes Redis"
\`\`\`

---

## Task 10: Update CLAUDE.md Documentation

**Files:**
- Modify: `CLAUDE.md`

**Step 1: Add Docker and Scheduler sections**

Add after line 51 (after Testing section):

\`\`\`markdown
### Docker Commands
\`\`\`bash
# Build Docker image
docker build -t vidtag:latest .

# Run with Docker Compose (recommended)
docker compose up --build

# Run standalone container (requires external Redis)
docker run -d \
  -p 8080:8080 \
  -e VIDTAG_CLAUDE_API_KEY=your-key \
  -e SPRING_DATA_REDIS_HOST=host.docker.internal \
  vidtag:latest

# View logs
docker compose logs app -f

# Stop and remove containers
docker compose down
\`\`\`

See [docs/DOCKER.md](docs/DOCKER.md) for comprehensive Docker deployment guide.
\`\`\`

Add after line 161 (at end of Implementation Status):

\`\`\`markdown
### Scheduled Processing
- **Playlist Processor Scheduler** runs every hour (configurable)
- Searches for playlist by name (default: "tag")
- Processes all videos through AI tagging workflow
- Enabled by default, can be disabled via `vidtag.scheduler.enabled=false`
- Configuration in `application.yaml`:
  - `vidtag.scheduler.enabled` - enable/disable scheduler
  - `vidtag.scheduler.fixed-delay-hours` - delay between runs (default: 1 hour)
  - `vidtag.scheduler.playlist-name` - playlist to process (default: "tag")

### Docker Deployment
- Multi-stage Dockerfile (JDK21 build, JRE21-alpine runtime)
- Docker Compose orchestration with Redis
- Health checks via Spring Actuator
- Environment variable configuration
- Non-root container user for security
- See [docs/DOCKER.md](docs/DOCKER.md) for deployment guide
\`\`\`

**Step 2: Run grep to verify changes**

Run: `grep -n "Docker" CLAUDE.md`
Expected: Shows the new Docker sections

**Step 3: Commit**

\`\`\`bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md with scheduler and Docker info

- Add Docker commands section
- Document scheduled playlist processor
- Reference detailed Docker guide
- Update implementation status"
\`\`\`

---

## Task 11: End-to-End Manual Test

**Files:**
- Create: `scripts/docker-test.sh`

**Step 1: Create test script**

Create executable test script:

\`\`\`bash
#!/bin/bash
# docker-test.sh - End-to-end Docker testing script

set -e

echo "=== VidTag Docker Test ==="
echo ""

# Check if .env exists
if [ ! -f .env ]; then
    echo "ERROR: .env file not found"
    echo "Please copy .env.example to .env and configure your API keys"
    exit 1
fi

echo "1. Building Docker image..."
docker compose build

echo ""
echo "2. Starting services..."
docker compose up -d

echo ""
echo "3. Waiting for services to be healthy..."
sleep 10

# Wait for health check
for i in {1..30}; do
    if docker compose ps | grep -q "healthy"; then
        echo "Services are healthy!"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "ERROR: Services failed to become healthy"
        docker compose logs
        docker compose down
        exit 1
    fi
    sleep 2
done

echo ""
echo "4. Testing health endpoint..."
curl -f http://localhost:8080/actuator/health || {
    echo "ERROR: Health check failed"
    docker compose logs app
    docker compose down
    exit 1
}

echo ""
echo "5. Checking scheduler logs..."
docker compose logs app | grep -i "scheduler" || echo "No scheduler logs yet (may need to wait)"

echo ""
echo "=== Test Successful ==="
echo ""
echo "Services are running. Access:"
echo "  - Application: http://localhost:8080"
echo "  - Swagger UI: http://localhost:8080/swagger-ui.html"
echo "  - Health: http://localhost:8080/actuator/health"
echo ""
echo "View logs: docker compose logs app -f"
echo "Stop services: docker compose down"
\`\`\`

**Step 2: Make script executable**

Run: `chmod +x scripts/docker-test.sh`

**Step 3: Test the script (only if .env exists)**

Run: `./scripts/docker-test.sh`
Expected: Script builds, starts, and verifies Docker services

**Step 4: Clean up**

Run: `docker compose down`

**Step 5: Commit**

\`\`\`bash
git add scripts/docker-test.sh
git commit -m "test: add Docker end-to-end test script

- Automated Docker build and startup
- Health check verification
- Scheduler log validation
- Helpful output with URLs
- Cleanup on failure"
\`\`\`

---

## Final Verification Checklist

Before considering this plan complete, verify:

- [ ] All tests pass: `./gradlew test`
- [ ] Application builds: `./gradlew build`
- [ ] Docker image builds: `docker build -t vidtag:test .`
- [ ] Docker Compose starts: `docker compose up` (with `.env` configured)
- [ ] Health endpoint works: `curl http://localhost:8080/actuator/health`
- [ ] Swagger UI accessible: http://localhost:8080/swagger-ui.html
- [ ] Scheduler logs appear: `docker compose logs app | grep scheduler`
- [ ] All files committed: `git status` shows clean working directory

## Post-Implementation Notes

### Future Enhancements

1. **Scheduler Improvements**
   - Add metrics for successful/failed runs
   - Implement distributed locking for multi-instance deployments
   - Add admin endpoint to trigger jobs manually
   - Support multiple playlists

2. **Docker Improvements**
   - Add volume mounts for persistent data
   - Implement container image scanning
   - Add Kubernetes manifests
   - Configure resource limits

3. **Observability**
   - Add structured logging
   - Implement Prometheus metrics
   - Add tracing with Spring Cloud Sleuth
   - Dashboard for monitoring scheduled jobs

### Known Limitations

- Scheduler uses local time (container timezone)
- No distributed lock (single instance only)
- API keys in environment variables (not secrets manager)
- No automatic retries for failed scheduled runs
- Manual testing required for full workflow

---

**Plan Version:** 1.0
**Created:** 2026-01-30
**Last Updated:** 2026-01-30
```

**Step 2: Verify plan file was created**

Run: `cat docs/plans/2026-01-30-scheduled-playlist-processor-and-dockerization.md | head -20`
Expected: Shows the plan header

**Step 3: Commit the plan**

```bash
git add docs/plans/2026-01-30-scheduled-playlist-processor-and-dockerization.md
git commit -m "docs: add implementation plan for scheduler and Docker

- 11 tasks covering scheduler, Docker, and testing
- TDD approach with tests before implementation
- Comprehensive Docker deployment guide
- End-to-end testing strategy"
```

---

Plan complete and saved to `docs/plans/2026-01-30-scheduled-playlist-processor-and-dockerization.md`.

## Execution Options

**1. Subagent-Driven (this session)** - I dispatch a fresh subagent per task, review between tasks, fast iteration in current session

**2. Parallel Session (separate)** - Open new session with executing-plans skill for batch execution with checkpoints

Which approach would you prefer?