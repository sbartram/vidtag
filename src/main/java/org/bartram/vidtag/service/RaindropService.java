package org.bartram.vidtag.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.bartram.vidtag.client.RaindropApiClient;
import org.bartram.vidtag.exception.ExternalServiceException;
import org.bartram.vidtag.exception.ResourceNotFoundException;
import org.bartram.vidtag.model.RaindropCollection;
import org.bartram.vidtag.model.RaindropTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Service for interacting with Raindrop.io API.
 * Includes circuit breaker protection and caching for tags.
 */
@Service
public class RaindropService {

    private static final Logger log = LoggerFactory.getLogger(RaindropService.class);

    private final RaindropApiClient raindropApiClient;

    public RaindropService(RaindropApiClient raindropApiClient) {
        this.raindropApiClient = raindropApiClient;
    }

    /**
     * Fetches all tags for a user with caching.
     * Protected by circuit breaker with fallback that throws RuntimeException.
     *
     * @param userId the Raindrop user ID
     * @return list of all user tags
     * @throws RuntimeException if API call fails
     */
    @Cacheable(value = "raindrop-tags", key = "#userId")
    @CircuitBreaker(name = "raindrop", fallbackMethod = "getUserTagsFallback")
    public List<RaindropTag> getUserTags(String userId) {
        log.debug("Fetching user tags for userId={}", userId);
        List<RaindropTag> tags = raindropApiClient.getUserTags(userId);
        log.info("Fetched {} tags for user {}", tags.size(), userId);
        return tags;
    }

    /**
     * Resolves a collection ID by title (case-insensitive).
     * Protected by circuit breaker with fallback that throws ExternalServiceException.
     *
     * @param userId the Raindrop user ID
     * @param collectionTitle the collection title to search for
     * @return collection ID
     * @throws ResourceNotFoundException if collection not found
     * @throws ExternalServiceException if API call fails
     */
    @CircuitBreaker(name = "raindrop", fallbackMethod = "resolveCollectionIdFallback")
    public Long resolveCollectionId(String userId, String collectionTitle) {
        log.debug("Resolving collection ID for userId={}, title={}", userId, collectionTitle);

        List<RaindropCollection> collections = raindropApiClient.getUserCollections(userId);

        return collections.stream()
            .filter(collection -> collection.title().equalsIgnoreCase(collectionTitle))
            .map(RaindropCollection::id)
            .findFirst()
            .orElseThrow(() -> {
                log.warn("Collection '{}' not found for user {}", collectionTitle, userId);
                return new ResourceNotFoundException("Collection", collectionTitle);
            });
    }

    /**
     * Checks if a bookmark already exists in a collection.
     * Protected by circuit breaker with fallback that throws RuntimeException.
     *
     * @param collectionId the collection ID
     * @param url the bookmark URL to check
     * @return true if bookmark exists, false otherwise
     * @throws RuntimeException if API call fails
     */
    @CircuitBreaker(name = "raindrop", fallbackMethod = "bookmarkExistsFallback")
    public boolean bookmarkExists(Long collectionId, String url) {
        log.debug("Checking if bookmark exists: collectionId={}, url={}", collectionId, url);
        boolean exists = raindropApiClient.bookmarkExists(collectionId, url);
        log.debug("Bookmark exists={} for url={}", exists, url);
        return exists;
    }

    /**
     * Creates a new bookmark in a collection.
     * Protected by circuit breaker with fallback that throws RuntimeException.
     *
     * @param collectionId the collection ID
     * @param url the bookmark URL
     * @param title the bookmark title
     * @param tags list of tag names to apply to the bookmark
     * @throws RuntimeException if API call fails
     */
    @CircuitBreaker(name = "raindrop", fallbackMethod = "createBookmarkFallback")
    public void createBookmark(Long collectionId, String url, String title, List<String> tags) {
        log.debug("Creating bookmark: collectionId={}, url={}, title={}, tags={}",
            collectionId, url, title, tags);
        raindropApiClient.createBookmark(collectionId, url, title, tags);
        log.info("Created bookmark in collection {}: {}", collectionId, url);
    }

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

    /**
     * Fallback method when Raindrop API circuit breaker is open for getUserTags.
     */
    private List<RaindropTag> getUserTagsFallback(String userId, Throwable throwable) {
        log.error("Raindrop API circuit breaker fallback triggered for getUserTags userId={}: {}",
            userId, throwable.getMessage());
        throw new ExternalServiceException("raindrop", "Raindrop API is currently unavailable", throwable);
    }

    /**
     * Fallback method when Raindrop API circuit breaker is open for resolveCollectionId.
     */
    private Long resolveCollectionIdFallback(String userId, String collectionTitle, Throwable throwable) {
        log.error("Raindrop API circuit breaker fallback triggered for resolveCollectionId userId={}, title={}: {}",
            userId, collectionTitle, throwable.getMessage());
        throw new ExternalServiceException("raindrop", "Raindrop API is currently unavailable", throwable);
    }

    /**
     * Fallback method when Raindrop API circuit breaker is open for bookmarkExists.
     */
    private boolean bookmarkExistsFallback(Long collectionId, String url, Throwable throwable) {
        log.error("Raindrop API circuit breaker fallback triggered for bookmarkExists collectionId={}, url={}: {}",
            collectionId, url, throwable.getMessage());
        throw new ExternalServiceException("raindrop", "Raindrop API is currently unavailable", throwable);
    }

    /**
     * Fallback method when Raindrop API circuit breaker is open for createBookmark.
     */
    private void createBookmarkFallback(Long collectionId, String url, String title, List<String> tags, Throwable throwable) {
        log.error("Raindrop API circuit breaker fallback triggered for createBookmark collectionId={}, url={}: {}",
            collectionId, url, throwable.getMessage());
        throw new ExternalServiceException("raindrop", "Failed to create bookmark - Raindrop API unavailable", throwable);
    }

    /**
     * Fallback method when Raindrop API circuit breaker is open for getUserCollections.
     */
    private List<String> getUserCollectionsFallback(Exception e) {
        log.warn("Circuit breaker active for getUserCollections, returning empty list: {}", e.getMessage());
        return Collections.emptyList();
    }
}
