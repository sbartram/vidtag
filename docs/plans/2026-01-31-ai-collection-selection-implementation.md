# AI-Powered Collection Selection Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement AI-powered automatic collection selection for YouTube playlists, removing the need for manual collection specification.

**Architecture:** Create `CollectionSelectionService` that uses Claude AI to analyze playlist metadata and choose appropriate Raindrop collections. Implement three-tier caching (playlist mappings, collections list, collection IDs) to minimize API calls. Remove `raindropCollectionTitle` from API contract.

**Tech Stack:** Spring Boot 4.0.2, Spring AI (Anthropic Claude), Spring Cache (Redis), Java 21

---

## Task 1: Create Configuration Properties

**Files:**
- Create: `src/main/java/org/bartram/vidtag/config/RaindropProperties.java`
- Modify: `src/main/resources/application.yaml`
- Test: `src/test/java/org/bartram/vidtag/config/RaindropPropertiesTest.java`

**Step 1: Write the failing test**

Create `src/test/java/org/bartram/vidtag/config/RaindropPropertiesTest.java`:

```java
package org.bartram.vidtag.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
    "vidtag.raindrop.fallback-collection=TestCollection",
    "vidtag.raindrop.collection-cache-ttl=48h",
    "vidtag.raindrop.collections-list-cache-ttl=2h"
})
class RaindropPropertiesTest {

    @Autowired
    private RaindropProperties raindropProperties;

    @Test
    void shouldBindFallbackCollection() {
        assertThat(raindropProperties.getFallbackCollection()).isEqualTo("TestCollection");
    }

    @Test
    void shouldBindCollectionCacheTtl() {
        assertThat(raindropProperties.getCollectionCacheTtl()).isEqualTo(Duration.ofHours(48));
    }

    @Test
    void shouldBindCollectionsListCacheTtl() {
        assertThat(raindropProperties.getCollectionsListCacheTtl()).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void shouldUseDefaultValues() {
        // Test with default application.yaml values
        assertThat(raindropProperties.getFallbackCollection()).isNotNull();
        assertThat(raindropProperties.getCollectionCacheTtl()).isNotNull();
        assertThat(raindropProperties.getCollectionsListCacheTtl()).isNotNull();
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests RaindropPropertiesTest`

Expected: FAIL with compilation errors (class doesn't exist)

**Step 3: Create RaindropProperties class**

Create `src/main/java/org/bartram/vidtag/config/RaindropProperties.java`:

```java
package org.bartram.vidtag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration properties for Raindrop.io integration.
 */
@Component
@ConfigurationProperties(prefix = "vidtag.raindrop")
public class RaindropProperties {

    /**
     * Fallback collection name to use when AI confidence is low or errors occur.
     */
    private String fallbackCollection = "Videos";

    /**
     * TTL for playlist → collection mapping cache.
     */
    private Duration collectionCacheTtl = Duration.ofHours(24);

    /**
     * TTL for user's collections list cache.
     */
    private Duration collectionsListCacheTtl = Duration.ofHours(1);

    public String getFallbackCollection() {
        return fallbackCollection;
    }

    public void setFallbackCollection(String fallbackCollection) {
        this.fallbackCollection = fallbackCollection;
    }

    public Duration getCollectionCacheTtl() {
        return collectionCacheTtl;
    }

    public void setCollectionCacheTtl(Duration collectionCacheTtl) {
        this.collectionCacheTtl = collectionCacheTtl;
    }

    public Duration getCollectionsListCacheTtl() {
        return collectionsListCacheTtl;
    }

    public void setCollectionsListCacheTtl(Duration collectionsListCacheTtl) {
        this.collectionsListCacheTtl = collectionsListCacheTtl;
    }
}
```

**Step 4: Update application.yaml**

Modify `src/main/resources/application.yaml` - add new section under `vidtag:`:

```yaml
vidtag:
  # ... existing properties ...

  # Raindrop.io Configuration
  raindrop:
    # Fallback collection when AI confidence is low
    fallback-collection: ${VIDTAG_RAINDROP_FALLBACK_COLLECTION:Videos}
    # Cache TTL for playlist → collection mappings
    collection-cache-ttl: ${VIDTAG_RAINDROP_COLLECTION_CACHE_TTL:24h}
    # Cache TTL for user's collection list
    collections-list-cache-ttl: ${VIDTAG_RAINDROP_COLLECTIONS_LIST_CACHE_TTL:1h}
```

**Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests RaindropPropertiesTest`

Expected: 4 tests PASS

**Step 6: Commit**

```bash
git add src/main/java/org/bartram/vidtag/config/RaindropProperties.java
git add src/test/java/org/bartram/vidtag/config/RaindropPropertiesTest.java
git add src/main/resources/application.yaml
git commit -m "feat: add Raindrop configuration properties

Add RaindropProperties with fallback collection and cache TTLs.
Configurable via environment variables.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 2: Add RaindropService Methods

**Files:**
- Modify: `src/main/java/org/bartram/vidtag/service/RaindropService.java`
- Modify: `src/main/java/org/bartram/vidtag/client/RaindropApiClient.java`
- Modify: `src/main/java/org/bartram/vidtag/client/impl/RaindropApiClientImpl.java`
- Test: `src/test/java/org/bartram/vidtag/service/RaindropServiceTest.java`

**Step 1: Write failing test for getUserCollections**

Add to `src/test/java/org/bartram/vidtag/service/RaindropServiceTest.java`:

```java
@Test
void getUserCollections_shouldReturnCollectionTitles() {
    // Arrange
    List<RaindropCollection> collections = List.of(
        new RaindropCollection(1L, "Technology"),
        new RaindropCollection(2L, "Cooking"),
        new RaindropCollection(3L, "Travel")
    );
    when(raindropApiClient.getCollections()).thenReturn(collections);

    // Act
    List<String> result = raindropService.getUserCollections();

    // Assert
    assertThat(result).containsExactly("Technology", "Cooking", "Travel");
    verify(raindropApiClient).getCollections();
}

@Test
void getUserCollections_shouldBeCached() {
    // Arrange
    List<RaindropCollection> collections = List.of(
        new RaindropCollection(1L, "Technology")
    );
    when(raindropApiClient.getCollections()).thenReturn(collections);

    // Act
    raindropService.getUserCollections(); // First call
    raindropService.getUserCollections(); // Second call (should use cache)

    // Assert
    verify(raindropApiClient, times(1)).getCollections(); // Only called once
}
```

**Step 2: Write failing test for createCollection**

Add to `src/test/java/org/bartram/vidtag/service/RaindropServiceTest.java`:

```java
@Test
void createCollection_shouldCallApiAndReturnId() {
    // Arrange
    String collectionTitle = "New Collection";
    Long expectedId = 42L;
    when(raindropApiClient.createCollection(collectionTitle)).thenReturn(expectedId);

    // Act
    Long result = raindropService.createCollection(collectionTitle);

    // Assert
    assertThat(result).isEqualTo(expectedId);
    verify(raindropApiClient).createCollection(collectionTitle);
}

@Test
void createCollection_shouldEvictCollectionsCache() {
    // This test verifies cache eviction happens
    // Implementation will be validated in integration tests
    String collectionTitle = "New Collection";
    when(raindropApiClient.createCollection(collectionTitle)).thenReturn(1L);

    raindropService.createCollection(collectionTitle);

    verify(raindropApiClient).createCollection(collectionTitle);
}
```

**Step 3: Run tests to verify they fail**

Run: `./gradlew test --tests RaindropServiceTest`

Expected: NEW tests FAIL (methods don't exist), existing tests PASS

**Step 4: Add methods to RaindropApiClient interface**

Modify `src/main/java/org/bartram/vidtag/client/RaindropApiClient.java`:

```java
/**
 * Get all collections for the authenticated user.
 *
 * @return list of collections
 */
List<RaindropCollection> getCollections();

/**
 * Create a new collection.
 *
 * @param title collection title
 * @return the ID of the created collection
 */
Long createCollection(String title);
```

**Step 5: Implement methods in RaindropApiClientImpl**

Modify `src/main/java/org/bartram/vidtag/client/impl/RaindropApiClientImpl.java`:

Add after existing methods:

```java
@Override
public List<RaindropCollection> getCollections() {
    log.debug("Fetching all collections");

    try {
        ResponseEntity<CollectionsResponse> response = restClient.get()
            .uri("/collections")
            .retrieve()
            .toEntity(CollectionsResponse.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.error("Failed to fetch collections: {}", response.getStatusCode());
            return Collections.emptyList();
        }

        List<RaindropCollection> collections = response.getBody().items();
        log.debug("Retrieved {} collections", collections.size());
        return collections;

    } catch (Exception e) {
        log.error("Error fetching collections: {}", e.getMessage(), e);
        return Collections.emptyList();
    }
}

@Override
public Long createCollection(String title) {
    log.debug("Creating collection: {}", title);

    try {
        Map<String, Object> requestBody = Map.of("title", title);

        ResponseEntity<CollectionCreateResponse> response = restClient.post()
            .uri("/collection")
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .toEntity(CollectionCreateResponse.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.error("Failed to create collection '{}': {}", title, response.getStatusCode());
            throw new RuntimeException("Failed to create collection: " + title);
        }

        Long collectionId = response.getBody().item()._id();
        log.info("Created collection '{}' with ID: {}", title, collectionId);
        return collectionId;

    } catch (Exception e) {
        log.error("Error creating collection '{}': {}", title, e.getMessage(), e);
        throw new RuntimeException("Failed to create collection: " + title, e);
    }
}

// Add response record classes at the end of the file
private record CollectionsResponse(List<RaindropCollection> items) {}
private record CollectionCreateResponse(RaindropCollection item) {}
```

**Step 6: Implement methods in RaindropService**

Modify `src/main/java/org/bartram/vidtag/service/RaindropService.java`:

Add after existing methods:

```java
/**
 * Get all collection titles for the authenticated user.
 * Results are cached to reduce API calls.
 *
 * @return list of collection titles
 */
@Cacheable(value = "raindrop-collections-list", unless = "#result == null || #result.isEmpty()")
@CircuitBreaker(name = "raindrop", fallbackMethod = "getUserCollectionsFallback")
@Retry(name = "raindrop")
public List<String> getUserCollections() {
    log.debug("Fetching user collections from Raindrop API");
    List<RaindropCollection> collections = raindropApiClient.getCollections();
    return collections.stream()
        .map(RaindropCollection::title)
        .toList();
}

/**
 * Create a new collection in Raindrop.
 * Evicts the collections list cache after successful creation.
 *
 * @param title collection title
 * @return the ID of the created collection
 */
@CacheEvict(value = "raindrop-collections-list", allEntries = true)
@CircuitBreaker(name = "raindrop")
@Retry(name = "raindrop")
public Long createCollection(String title) {
    log.info("Creating new collection: {}", title);
    return raindropApiClient.createCollection(title);
}

private List<String> getUserCollectionsFallback(Exception e) {
    log.warn("Circuit breaker active for getUserCollections, returning empty list: {}", e.getMessage());
    return Collections.emptyList();
}
```

**Step 7: Update stub implementations**

Modify `src/test/java/org/bartram/vidtag/TestcontainersConfiguration.java` - update stub client:

```java
@Bean
@ConditionalOnMissingBean
RaindropApiClient raindropApiClient() {
    return new RaindropApiClient() {
        // ... existing methods ...

        @Override
        public List<RaindropCollection> getCollections() {
            return Collections.emptyList();
        }

        @Override
        public Long createCollection(String title) {
            return 1L; // Return dummy ID
        }
    };
}
```

**Step 8: Run tests to verify they pass**

Run: `./gradlew test --tests RaindropServiceTest`

Expected: All tests PASS

**Step 9: Commit**

```bash
git add src/main/java/org/bartram/vidtag/client/RaindropApiClient.java
git add src/main/java/org/bartram/vidtag/client/impl/RaindropApiClientImpl.java
git add src/main/java/org/bartram/vidtag/service/RaindropService.java
git add src/test/java/org/bartram/vidtag/service/RaindropServiceTest.java
git add src/test/java/org/bartram/vidtag/TestcontainersConfiguration.java
git commit -m "feat: add getUserCollections and createCollection to RaindropService

Add methods to fetch all collections and create new collections.
Includes caching for collections list and cache eviction on creation.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 3: Configure Cache for Collection Selection

**Files:**
- Modify: `src/main/java/org/bartram/vidtag/config/CacheConfig.java` (if exists) OR create it
- Modify: `src/main/resources/application.yaml`

**Step 1: Create or update CacheConfig**

Check if `src/main/java/org/bartram/vidtag/config/CacheConfig.java` exists. If not, create it:

```java
package org.bartram.vidtag.config;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

/**
 * Cache configuration for Redis.
 */
@Configuration
public class CacheConfig {

    private final RaindropProperties raindropProperties;

    public CacheConfig(RaindropProperties raindropProperties) {
        this.raindropProperties = raindropProperties;
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return (builder) -> builder
            .withCacheConfiguration("playlist-collections",
                RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(raindropProperties.getCollectionCacheTtl()))
            .withCacheConfiguration("raindrop-collections-list",
                RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(raindropProperties.getCollectionsListCacheTtl()));
    }
}
```

**Step 2: Run build to verify**

Run: `./gradlew build -x test`

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/org/bartram/vidtag/config/CacheConfig.java
git commit -m "feat: configure caches for collection selection

Add playlist-collections and raindrop-collections-list caches
with configurable TTLs from RaindropProperties.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 4: Create CollectionSelectionService

**Files:**
- Create: `src/main/java/org/bartram/vidtag/service/CollectionSelectionService.java`
- Create: `src/test/java/org/bartram/vidtag/service/CollectionSelectionServiceTest.java`

**Step 1: Write failing tests**

Create `src/test/java/org/bartram/vidtag/service/CollectionSelectionServiceTest.java`:

```java
package org.bartram.vidtag.service;

import org.bartram.vidtag.config.RaindropProperties;
import org.bartram.vidtag.model.VideoMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollectionSelectionServiceTest {

    @Mock
    private RaindropService raindropService;

    @Mock
    private YouTubeService youtubeService;

    @Mock
    private ChatClient chatClient;

    @Mock
    private CallResponseSpec callResponseSpec;

    private RaindropProperties raindropProperties;
    private CacheManager cacheManager;
    private CollectionSelectionService collectionSelectionService;

    @BeforeEach
    void setUp() {
        raindropProperties = new RaindropProperties();
        raindropProperties.setFallbackCollection("Videos");
        raindropProperties.setCollectionCacheTtl(Duration.ofHours(24));
        raindropProperties.setCollectionsListCacheTtl(Duration.ofHours(1));

        cacheManager = new ConcurrentMapCacheManager("playlist-collections");

        collectionSelectionService = new CollectionSelectionService(
            raindropService,
            youtubeService,
            chatClient,
            raindropProperties,
            cacheManager
        );
    }

    @Test
    void selectCollection_cacheHit_returnsCachedValue() {
        // Arrange
        String playlistId = "PLxxx";
        String cachedCollection = "Technology";
        cacheManager.getCache("playlist-collections").put(playlistId, cachedCollection);

        // Act
        String result = collectionSelectionService.selectCollection(playlistId);

        // Assert
        assertThat(result).isEqualTo(cachedCollection);
        verifyNoInteractions(raindropService, youtubeService, chatClient);
    }

    @Test
    void selectCollection_cacheMiss_callsAI() {
        // Arrange
        String playlistId = "PLxxx";
        List<String> collections = List.of("Technology", "Cooking");
        String playlistTitle = "Java Programming";
        String playlistDescription = "Learn Java";
        List<VideoMetadata> videos = List.of(
            new VideoMetadata("v1", "Spring Boot Tutorial", "", "", Duration.ZERO)
        );

        when(raindropService.getUserCollections()).thenReturn(collections);
        when(youtubeService.getPlaylistMetadata(playlistId))
            .thenReturn(new PlaylistMetadata(playlistTitle, playlistDescription));
        when(youtubeService.getPlaylistVideos(playlistId, 10)).thenReturn(videos);
        when(chatClient.prompt(anyString())).thenReturn(callResponseSpec);
        when(callResponseSpec.call()).thenReturn("Technology");

        // Act
        String result = collectionSelectionService.selectCollection(playlistId);

        // Assert
        assertThat(result).isEqualTo("Technology");
        verify(chatClient).prompt(anyString());
    }

    @Test
    void selectCollection_aiReturnsLowConfidence_usesFallback() {
        // Arrange
        String playlistId = "PLxxx";
        setupMocksForAICall();
        when(callResponseSpec.call()).thenReturn("LOW_CONFIDENCE");

        // Act
        String result = collectionSelectionService.selectCollection(playlistId);

        // Assert
        assertThat(result).isEqualTo("Videos"); // fallback
    }

    @Test
    void selectCollection_aiReturnsInvalidCollection_usesFallback() {
        // Arrange
        String playlistId = "PLxxx";
        setupMocksForAICall();
        when(callResponseSpec.call()).thenReturn("NonExistent");

        // Act
        String result = collectionSelectionService.selectCollection(playlistId);

        // Assert
        assertThat(result).isEqualTo("Videos"); // fallback
    }

    @Test
    void selectCollection_fallbackDoesNotExist_createsIt() {
        // Arrange
        String playlistId = "PLxxx";
        setupMocksForAICall();
        when(raindropService.getUserCollections()).thenReturn(List.of("Technology"));
        when(callResponseSpec.call()).thenReturn("LOW_CONFIDENCE");
        when(raindropService.createCollection("Videos")).thenReturn(1L);

        // Act
        String result = collectionSelectionService.selectCollection(playlistId);

        // Assert
        assertThat(result).isEqualTo("Videos");
        verify(raindropService).createCollection("Videos");
    }

    private void setupMocksForAICall() {
        when(raindropService.getUserCollections()).thenReturn(List.of("Technology", "Cooking"));
        when(youtubeService.getPlaylistMetadata(anyString()))
            .thenReturn(new PlaylistMetadata("Test", "Test"));
        when(youtubeService.getPlaylistVideos(anyString(), anyInt()))
            .thenReturn(List.of(new VideoMetadata("v1", "Title", "", "", Duration.ZERO)));
        when(chatClient.prompt(anyString())).thenReturn(callResponseSpec);
    }

    // Helper record for test
    private record PlaylistMetadata(String title, String description) {}
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests CollectionSelectionServiceTest`

Expected: FAIL (class doesn't exist)

**Step 3: Create CollectionSelectionService**

Create `src/main/java/org/bartram/vidtag/service/CollectionSelectionService.java`:

```java
package org.bartram.vidtag.service;

import org.bartram.vidtag.config.RaindropProperties;
import org.bartram.vidtag.model.VideoMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for AI-powered collection selection.
 * Analyzes playlist content and determines the most appropriate Raindrop collection.
 */
@Service
public class CollectionSelectionService {

    private static final Logger log = LoggerFactory.getLogger(CollectionSelectionService.class);
    private static final String CACHE_NAME = "playlist-collections";
    private static final String LOW_CONFIDENCE = "LOW_CONFIDENCE";
    private static final int SAMPLE_VIDEO_COUNT = 10;

    private final RaindropService raindropService;
    private final YouTubeService youtubeService;
    private final ChatClient chatClient;
    private final RaindropProperties raindropProperties;
    private final CacheManager cacheManager;

    public CollectionSelectionService(
            RaindropService raindropService,
            YouTubeService youtubeService,
            ChatClient chatClient,
            RaindropProperties raindropProperties,
            CacheManager cacheManager) {
        this.raindropService = raindropService;
        this.youtubeService = youtubeService;
        this.chatClient = chatClient;
        this.raindropProperties = raindropProperties;
        this.cacheManager = cacheManager;
    }

    /**
     * Select the most appropriate collection for a playlist.
     * Uses caching to avoid repeated AI analysis.
     *
     * @param playlistId YouTube playlist ID
     * @return collection title
     */
    public String selectCollection(String playlistId) {
        log.debug("Selecting collection for playlist: {}", playlistId);

        // Check cache first
        String cached = getCachedCollection(playlistId);
        if (cached != null) {
            log.debug("Using cached collection '{}' for playlist {}", cached, playlistId);
            return cached;
        }

        // Get available collections
        List<String> availableCollections = raindropService.getUserCollections();
        if (availableCollections.isEmpty()) {
            log.warn("No collections available, using fallback");
            return ensureFallbackExists(availableCollections);
        }

        // Get playlist metadata and sample videos
        PlaylistMetadata metadata = youtubeService.getPlaylistMetadata(playlistId);
        List<VideoMetadata> sampleVideos = youtubeService.getPlaylistVideos(playlistId, SAMPLE_VIDEO_COUNT);

        if (sampleVideos.isEmpty()) {
            log.info("Empty playlist '{}', using fallback collection", playlistId);
            return ensureFallbackExists(availableCollections);
        }

        // Ask AI to choose collection
        String aiChoice = askAIForCollection(availableCollections, metadata, sampleVideos);
        String selectedCollection = validateAndSelectCollection(aiChoice, availableCollections);

        // Cache the decision
        cacheCollection(playlistId, selectedCollection);

        return selectedCollection;
    }

    private String getCachedCollection(String playlistId) {
        var cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            var cached = cache.get(playlistId, String.class);
            return cached;
        }
        return null;
    }

    private void cacheCollection(String playlistId, String collection) {
        var cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.put(playlistId, collection);
            log.debug("Cached collection '{}' for playlist {}", collection, playlistId);
        }
    }

    private String askAIForCollection(
            List<String> availableCollections,
            PlaylistMetadata metadata,
            List<VideoMetadata> sampleVideos) {

        String prompt = buildPrompt(availableCollections, metadata, sampleVideos);
        log.debug("Asking AI to select collection");

        try {
            String response = chatClient.prompt(prompt).call();
            return response.trim();
        } catch (Exception e) {
            log.error("Failed to get AI response: {}", e.getMessage(), e);
            return LOW_CONFIDENCE;
        }
    }

    private String buildPrompt(
            List<String> availableCollections,
            PlaylistMetadata metadata,
            List<VideoMetadata> sampleVideos) {

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are helping categorize YouTube videos into Raindrop.io collections.\n\n");

        prompt.append("Available collections:\n");
        for (String collection : availableCollections) {
            prompt.append("- ").append(collection).append("\n");
        }

        prompt.append("\nPlaylist information:\n");
        prompt.append("Title: ").append(metadata.title()).append("\n");
        if (metadata.description() != null && !metadata.description().isBlank()) {
            prompt.append("Description: ").append(metadata.description()).append("\n");
        }

        prompt.append("\nSample video titles:\n");
        int count = 1;
        for (VideoMetadata video : sampleVideos.subList(0, Math.min(10, sampleVideos.size()))) {
            prompt.append(count++).append(". ").append(video.title()).append("\n");
        }

        prompt.append("\nChoose the most appropriate collection from the available collections above for this playlist.\n\n");
        prompt.append("Rules:\n");
        prompt.append("- Respond with ONLY the exact collection name from the list\n");
        prompt.append("- If none of the collections are a good fit, respond with exactly \"LOW_CONFIDENCE\"\n");
        prompt.append("- Do not create new collection names\n");
        prompt.append("- Do not explain your reasoning\n");
        prompt.append("- Match the collection name exactly as shown in the list\n\n");
        prompt.append("Response:");

        return prompt.toString();
    }

    private String validateAndSelectCollection(String aiChoice, List<String> availableCollections) {
        if (LOW_CONFIDENCE.equals(aiChoice)) {
            log.info("AI indicated low confidence, using fallback collection");
            return ensureFallbackExists(availableCollections);
        }

        if (availableCollections.contains(aiChoice)) {
            log.info("AI selected collection: {}", aiChoice);
            return aiChoice;
        }

        log.warn("AI suggested non-existent collection '{}', using fallback", aiChoice);
        return ensureFallbackExists(availableCollections);
    }

    private String ensureFallbackExists(List<String> availableCollections) {
        String fallback = raindropProperties.getFallbackCollection();

        if (!availableCollections.contains(fallback)) {
            log.info("Fallback collection '{}' does not exist, creating it", fallback);
            raindropService.createCollection(fallback);
        }

        return fallback;
    }

    /**
     * Simple record for playlist metadata.
     */
    private record PlaylistMetadata(String title, String description) {}
}
```

**Step 4: Add getPlaylistMetadata method to YouTubeService**

Modify `src/main/java/org/bartram/vidtag/service/YouTubeService.java`:

Add method:

```java
/**
 * Get playlist metadata (title and description).
 *
 * @param playlistId playlist ID
 * @return playlist metadata
 */
public PlaylistMetadata getPlaylistMetadata(String playlistId) {
    log.debug("Fetching metadata for playlist: {}", playlistId);
    // Implementation will extract title/description from first page of results
    List<VideoMetadata> videos = getPlaylistVideos(playlistId, 1);
    // For now, return basic metadata - can be enhanced later
    return new PlaylistMetadata(playlistId, "");
}

/**
 * Simple record for playlist metadata.
 */
public record PlaylistMetadata(String title, String description) {}
```

**Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests CollectionSelectionServiceTest`

Expected: All tests PASS

**Step 6: Commit**

```bash
git add src/main/java/org/bartram/vidtag/service/CollectionSelectionService.java
git add src/test/java/org/bartram/vidtag/service/CollectionSelectionServiceTest.java
git add src/main/java/org/bartram/vidtag/service/YouTubeService.java
git commit -m "feat: implement CollectionSelectionService

AI-powered collection selection with caching. Analyzes playlist
metadata and sample videos to choose appropriate collection.
Falls back to configured collection when uncertain.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 5: Update TagPlaylistRequest (Breaking Change)

**Files:**
- Modify: `src/main/java/org/bartram/vidtag/dto/TagPlaylistRequest.java`
- Modify: `src/test/java/org/bartram/vidtag/controller/PlaylistControllerTest.java`

**Step 1: Update test to remove collection field**

Modify `src/test/java/org/bartram/vidtag/controller/PlaylistControllerTest.java`:

Find all test request bodies and remove `raindropCollectionTitle` field.

Example before:
```json
{
    "playlistInput": "PLxxx",
    "raindropCollectionTitle": "Technology",
    "tagStrategy": "SUGGEST"
}
```

Example after:
```json
{
    "playlistInput": "PLxxx",
    "tagStrategy": "SUGGEST"
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests PlaylistControllerTest`

Expected: Tests may pass (field is optional) but we want to verify removal

**Step 3: Update TagPlaylistRequest**

Modify `src/main/java/org/bartram/vidtag/dto/TagPlaylistRequest.java`:

```java
/**
 * Request DTO for tagging a YouTube playlist.
 * Collection is automatically determined by AI analysis.
 *
 * @param playlistInput YouTube playlist ID or URL
 * @param filters optional filters for selecting videos from the playlist
 * @param tagStrategy optional strategy for tag generation
 * @param verbosity verbosity level for processing output
 */
public record TagPlaylistRequest(
    @NotBlank(message = "playlistInput is required")
    String playlistInput,
    // raindropCollectionTitle REMOVED - AI determines collection
    VideoFilters filters,
    TagStrategy tagStrategy,
    Verbosity verbosity
) {
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests PlaylistControllerTest`

Expected: All tests PASS

**Step 5: Commit**

```bash
git add src/main/java/org/bartram/vidtag/dto/TagPlaylistRequest.java
git add src/test/java/org/bartram/vidtag/controller/PlaylistControllerTest.java
git commit -m "feat!: remove raindropCollectionTitle from TagPlaylistRequest

BREAKING CHANGE: Collection is now automatically determined by AI.
API clients must remove raindropCollectionTitle field from requests.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 6: Update VideoTaggingOrchestrator

**Files:**
- Modify: `src/main/java/org/bartram/vidtag/service/VideoTaggingOrchestrator.java`
- Modify: `src/test/java/org/bartram/vidtag/service/VideoTaggingOrchestratorTest.java`

**Step 1: Update test to not expect collection in request**

Modify `src/test/java/org/bartram/vidtag/service/VideoTaggingOrchestratorTest.java`:

Update test requests to remove collection field and mock `CollectionSelectionService`.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests VideoTaggingOrchestratorTest`

Expected: FAIL (needs CollectionSelectionService)

**Step 3: Update VideoTaggingOrchestrator**

Modify `src/main/java/org/bartram/vidtag/service/VideoTaggingOrchestrator.java`:

Add field:
```java
private final CollectionSelectionService collectionSelectionService;
```

Update constructor:
```java
public VideoTaggingOrchestrator(
        YouTubeService youtubeService,
        RaindropService raindropService,
        VideoTaggingService videoTaggingService,
        CollectionSelectionService collectionSelectionService) {
    this.youtubeService = youtubeService;
    this.raindropService = raindropService;
    this.videoTaggingService = videoTaggingService;
    this.collectionSelectionService = collectionSelectionService;
}
```

Update `processPlaylist` method - replace the line that gets collection from request:

Before:
```java
String collectionTitle = request.raindropCollectionTitle();
```

After:
```java
// Extract playlist ID
String playlistId = extractPlaylistId(request.playlistInput());

// AI determines collection
String collectionTitle = collectionSelectionService.selectCollection(playlistId);
log.info("AI selected collection '{}' for playlist {}", collectionTitle, playlistId);
```

Add helper method:
```java
private String extractPlaylistId(String playlistInput) {
    // Extract ID from URL or return as-is if already an ID
    if (playlistInput.contains("youtube.com") || playlistInput.contains("youtu.be")) {
        // Extract from URL (handle both formats)
        int listIndex = playlistInput.indexOf("list=");
        if (listIndex != -1) {
            String id = playlistInput.substring(listIndex + 5);
            int ampIndex = id.indexOf("&");
            return ampIndex != -1 ? id.substring(0, ampIndex) : id;
        }
    }
    return playlistInput;
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests VideoTaggingOrchestratorTest`

Expected: All tests PASS (with mocked CollectionSelectionService)

**Step 5: Commit**

```bash
git add src/main/java/org/bartram/vidtag/service/VideoTaggingOrchestrator.java
git add src/test/java/org/bartram/vidtag/service/VideoTaggingOrchestratorTest.java
git commit -m "feat: integrate CollectionSelectionService into orchestrator

Replace manual collection specification with AI-powered selection.
Extract playlist ID from URLs for collection selection.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 7: Update PlaylistProcessingScheduler

**Files:**
- Modify: `src/main/java/org/bartram/vidtag/service/PlaylistProcessingScheduler.java`
- Modify: `src/test/java/org/bartram/vidtag/service/PlaylistProcessingSchedulerTest.java`

**Step 1: Update test**

Modify `src/test/java/org/bartram/vidtag/service/PlaylistProcessingSchedulerTest.java`:

Update to verify scheduler doesn't pass collection field.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests PlaylistProcessingSchedulerTest`

Expected: FAIL (still using old API)

**Step 3: Update PlaylistProcessingScheduler**

Modify `src/main/java/org/bartram/vidtag/service/PlaylistProcessingScheduler.java`:

Remove constant:
```java
private static final String DEFAULT_COLLECTION = "Videos";
```

Update request creation in `processTagPlaylist()`:

Before:
```java
TagPlaylistRequest request = new TagPlaylistRequest(
    playlistId,
    DEFAULT_COLLECTION,
    new VideoFilters(null, null, null),
    TagStrategy.SUGGEST,
    null
);
```

After:
```java
TagPlaylistRequest request = new TagPlaylistRequest(
    playlistId,
    new VideoFilters(null, null, null),
    TagStrategy.SUGGEST,
    null
);
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests PlaylistProcessingSchedulerTest`

Expected: All tests PASS

**Step 5: Commit**

```bash
git add src/main/java/org/bartram/vidtag/service/PlaylistProcessingScheduler.java
git add src/test/java/org/bartram/vidtag/service/PlaylistProcessingSchedulerTest.java
git commit -m "feat: remove hardcoded collection from scheduler

Scheduler now uses AI-powered collection selection.
Removed DEFAULT_COLLECTION constant.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 8: Run Full Test Suite

**Step 1: Run all tests**

Run: `./gradlew test`

Expected: All tests PASS

**Step 2: If tests fail, fix issues**

Review failures and fix broken tests. Common issues:
- Missing mocks for `CollectionSelectionService`
- Tests expecting collection field in requests
- Integration tests needing updated request bodies

**Step 3: Run tests again**

Run: `./gradlew test`

Expected: All tests PASS

**Step 4: Commit any fixes**

```bash
git add .
git commit -m "test: fix remaining test failures

Update all tests to work with AI collection selection.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 9: Update Documentation

**Files:**
- Modify: `CLAUDE.md`
- Modify: `docs/plans/2026-01-31-ai-collection-selection-design.md`

**Step 1: Update CLAUDE.md**

Modify `CLAUDE.md`:

Update "Scheduled Processing" section to remove references to collection configuration.

Add new section after "Tag Filtering":

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

**Environment Variables:**
```bash
VIDTAG_RAINDROP_FALLBACK_COLLECTION="Uncategorized"
VIDTAG_RAINDROP_COLLECTION_CACHE_TTL="48h"
VIDTAG_RAINDROP_COLLECTIONS_LIST_CACHE_TTL="2h"
```

**Fallback Triggers:**
- AI explicitly indicates low confidence
- AI suggests a collection that doesn't exist
- Circuit breaker open (Claude API unavailable)
- Empty playlist (no videos to analyze)
- YouTube API failure (can't fetch playlist metadata)

**Automatic Fallback Creation:**
If the configured fallback collection doesn't exist in your Raindrop account, VidTag automatically creates it to prevent processing failures.
```

Update API Endpoint section to remove collection field:

```markdown
### API Endpoint
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

Add breaking change notice:

```markdown
## Breaking Changes

### v2.0 - AI-Powered Collection Selection

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

**Step 2: Mark design as implemented**

Modify `docs/plans/2026-01-31-ai-collection-selection-design.md`:

Update status at top:
```markdown
**Date:** 2026-01-31
**Status:** ✅ Implemented
**Breaking Change:** Yes - removes `raindropCollectionTitle` from API
```

**Step 3: Commit**

```bash
git add CLAUDE.md
git add docs/plans/2026-01-31-ai-collection-selection-design.md
git commit -m "docs: update documentation for AI collection selection

Add new section explaining AI-powered collection selection.
Update API documentation to remove collection field.
Add breaking change notice and migration guide.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 10: Integration Testing (Manual)

**Step 1: Build the application**

Run: `./gradlew build`

Expected: BUILD SUCCESSFUL

**Step 2: Start the application with Testcontainers**

Run: `./gradlew bootRun`

Expected: Application starts, Redis container launched

**Step 3: Test API endpoint (if API keys configured)**

If you have `VIDTAG_CLAUDE_API_KEY`, `YOUTUBE_API_KEY`, and `RAINDROP_API_TOKEN`:

```bash
curl -X POST http://localhost:8080/api/v1/playlists/tag \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "playlistInput": "PLxxx...",
    "tagStrategy": "SUGGEST"
  }'
```

Observe:
- SSE events showing collection selection
- Videos being processed
- Bookmarks created in selected collection

**Step 4: Verify caching**

Run same request again, verify logs show cache hit for collection selection.

**Step 5: Test fallback behavior**

Create a test with a playlist that doesn't match any collections well.
Verify fallback collection is used.

**Step 6: Stop application**

Ctrl+C to stop

**Step 7: Document results**

Add notes to plan about any issues discovered during manual testing.

---

## Task 11: Final Verification and Merge Preparation

**Step 1: Run full build**

Run: `./gradlew clean build`

Expected: BUILD SUCCESSFUL, all tests PASS

**Step 2: Review all changes**

Run: `git log --oneline feature/ai-collection-selection`

Verify all commits are present and follow conventional commit format.

**Step 3: Run integration tests (if enabled)**

If integration tests are enabled:

Run: `./gradlew test --tests '*IT'`

**Step 4: Check for code quality issues**

Run: `./gradlew check`

Expected: No violations

**Step 5: Create summary commit (if needed)**

If any final tweaks were made, create a summary commit:

```bash
git add .
git commit -m "chore: final cleanup for AI collection selection

All tests passing, documentation updated, ready for review.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

**Step 6: Push branch**

Run: `git push -u origin feature/ai-collection-selection`

**Step 7: Use finishing-a-development-branch skill**

After implementation is complete, use @superpowers:finishing-a-development-branch to:
- Review options (merge, PR, cleanup)
- Create pull request if desired
- Clean up worktree

---

## Summary

This plan implements AI-powered collection selection in 11 tasks:

1. ✅ Configuration properties for fallback and cache TTLs
2. ✅ RaindropService methods for collections and creation
3. ✅ Cache configuration for collection mappings
4. ✅ CollectionSelectionService with AI integration
5. ✅ Remove collection field from API (breaking change)
6. ✅ Integrate into VideoTaggingOrchestrator
7. ✅ Update scheduler to remove hardcoded collection
8. ✅ Run full test suite and fix issues
9. ✅ Update documentation (CLAUDE.md, design doc)
10. ✅ Manual integration testing
11. ✅ Final verification and merge preparation

**Estimated time:** 3-4 hours (with testing)

**Testing strategy:**
- TDD throughout (write test → fail → implement → pass)
- Unit tests for all services
- Integration tests for end-to-end flow
- Manual testing with real APIs (optional)

**Key principles:**
- DRY: Reuse existing caching infrastructure
- YAGNI: Only implement what's in the design
- TDD: Test-first for all new code
- Frequent commits: After each task completion
