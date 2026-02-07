package org.bartram.vidtag.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.vidtag.client.RaindropApiClient;
import org.bartram.vidtag.exception.ExternalServiceException;
import org.bartram.vidtag.exception.ResourceNotFoundException;
import org.bartram.vidtag.model.Raindrop;
import org.bartram.vidtag.model.RaindropCollection;
import org.bartram.vidtag.model.RaindropTag;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Service for interacting with Raindrop.io API.
 * Includes circuit breaker protection and caching for tags.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RaindropService {

    private final RaindropApiClient raindropApiClient;

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
        log.atDebug()
                .setMessage("Fetching user tags for userId={}")
                .addArgument(userId)
                .log();
        List<RaindropTag> tags = raindropApiClient.getUserTags(userId);
        log.atInfo()
                .setMessage("Fetched {} tags for user {}")
                .addArgument(tags.size())
                .addArgument(userId)
                .log();
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
        log.atDebug()
                .setMessage("Resolving collection ID for userId={}, title={}")
                .addArgument(userId)
                .addArgument(collectionTitle)
                .log();

        List<RaindropCollection> collections = raindropApiClient.getUserCollections(userId);

        return collections.stream()
                .filter(collection -> collection.title().equalsIgnoreCase(collectionTitle))
                .map(RaindropCollection::id)
                .findFirst()
                .orElseThrow(() -> {
                    log.atWarn()
                            .setMessage("Collection '{}' not found for user {}")
                            .addArgument(collectionTitle)
                            .addArgument(userId)
                            .log();
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
        log.atDebug()
                .setMessage("Checking if bookmark exists: collectionId={}, url={}")
                .addArgument(collectionId)
                .addArgument(url)
                .log();
        boolean exists = raindropApiClient.bookmarkExists(collectionId, url);
        log.atDebug()
                .setMessage("Bookmark exists={} for url={}")
                .addArgument(exists)
                .addArgument(url)
                .log();
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
        log.atDebug()
                .setMessage("Creating bookmark: collectionId={}, url={}, title={}, tags={}")
                .addArgument(collectionId)
                .addArgument(url)
                .addArgument(title)
                .addArgument(tags)
                .log();
        raindropApiClient.createBookmark(collectionId, url, title, tags);
        log.atInfo()
                .setMessage("Created bookmark in collection {}: {}")
                .addArgument(collectionId)
                .addArgument(url)
                .log();
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
        log.atDebug().setMessage("Fetching user collections from Raindrop API").log();
        List<RaindropCollection> collections = raindropApiClient.getCollections();
        return collections.stream().map(RaindropCollection::title).toList();
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
        log.atInfo()
                .setMessage("Creating new collection: {}")
                .addArgument(title)
                .log();
        return raindropApiClient.createCollection(title);
    }

    /**
     * Fetches all unsorted bookmarks (collection ID -1).
     * Protected by circuit breaker with fallback.
     *
     * @return list of unsorted raindrops
     */
    @CircuitBreaker(name = "raindrop", fallbackMethod = "getUnsortedRaindropsFallback")
    @Retry(name = "raindrop")
    public List<Raindrop> getUnsortedRaindrops() {
        log.atDebug().setMessage("Fetching unsorted raindrops").log();
        List<Raindrop> raindrops = raindropApiClient.getRaindrops(-1L);
        log.atInfo()
                .setMessage("Fetched {} unsorted raindrops")
                .addArgument(raindrops.size())
                .log();
        return raindrops;
    }

    /**
     * Updates a raindrop's collection and tags.
     * Protected by circuit breaker with fallback.
     *
     * @param raindropId the raindrop ID to update
     * @param collectionId the target collection ID
     * @param tags list of tag names to apply
     */
    @CircuitBreaker(name = "raindrop", fallbackMethod = "updateRaindropFallback")
    @Retry(name = "raindrop")
    public void updateRaindrop(Long raindropId, Long collectionId, List<String> tags) {
        log.atDebug()
                .setMessage("Updating raindrop {}: collection={}, tags={}")
                .addArgument(raindropId)
                .addArgument(collectionId)
                .addArgument(tags)
                .log();
        raindropApiClient.updateRaindrop(raindropId, collectionId, tags);
        log.atInfo()
                .setMessage("Updated raindrop {} to collection {} with {} tags")
                .addArgument(raindropId)
                .addArgument(collectionId)
                .addArgument(tags.size())
                .log();
    }

    /**
     * Fallback method when Raindrop API circuit breaker is open for getUserTags.
     */
    private List<RaindropTag> getUserTagsFallback(String userId, Throwable throwable) {
        log.atError()
                .setMessage("Raindrop API circuit breaker fallback triggered for getUserTags userId={}: {}")
                .addArgument(userId)
                .addArgument(throwable.getMessage())
                .log();
        throw new ExternalServiceException("raindrop", "Raindrop API is currently unavailable", throwable);
    }

    /**
     * Fallback method when Raindrop API circuit breaker is open for resolveCollectionId.
     */
    private Long resolveCollectionIdFallback(String userId, String collectionTitle, Throwable throwable) {
        log.atError()
                .setMessage(
                        "Raindrop API circuit breaker fallback triggered for resolveCollectionId userId={}, title={}: {}")
                .addArgument(userId)
                .addArgument(collectionTitle)
                .addArgument(throwable.getMessage())
                .log();
        throw new ExternalServiceException("raindrop", "Raindrop API is currently unavailable", throwable);
    }

    /**
     * Fallback method when Raindrop API circuit breaker is open for bookmarkExists.
     */
    private boolean bookmarkExistsFallback(Long collectionId, String url, Throwable throwable) {
        log.atError()
                .setMessage(
                        "Raindrop API circuit breaker fallback triggered for bookmarkExists collectionId={}, url={}: {}")
                .addArgument(collectionId)
                .addArgument(url)
                .addArgument(throwable.getMessage())
                .log();
        throw new ExternalServiceException("raindrop", "Raindrop API is currently unavailable", throwable);
    }

    /**
     * Fallback method when Raindrop API circuit breaker is open for createBookmark.
     */
    private void createBookmarkFallback(
            Long collectionId, String url, String title, List<String> tags, Throwable throwable) {
        log.atError()
                .setMessage(
                        "Raindrop API circuit breaker fallback triggered for createBookmark collectionId={}, url={}: {}")
                .addArgument(collectionId)
                .addArgument(url)
                .addArgument(throwable.getMessage())
                .log();
        throw new ExternalServiceException(
                "raindrop", "Failed to create bookmark - Raindrop API unavailable", throwable);
    }

    /**
     * Fallback method when Raindrop API circuit breaker is open for getUserCollections.
     */
    private List<String> getUserCollectionsFallback(Exception e) {
        log.atWarn()
                .setMessage("Circuit breaker active for getUserCollections, returning empty list: {}")
                .addArgument(e.getMessage())
                .log();
        return Collections.emptyList();
    }

    /**
     * Fallback method when Raindrop API circuit breaker is open for getUnsortedRaindrops.
     */
    private List<Raindrop> getUnsortedRaindropsFallback(Throwable throwable) {
        log.atError()
                .setMessage("Raindrop API circuit breaker fallback triggered for getUnsortedRaindrops: {}")
                .addArgument(throwable.getMessage())
                .log();
        throw new ExternalServiceException("raindrop", "Raindrop API is currently unavailable", throwable);
    }

    /**
     * Fallback method when Raindrop API circuit breaker is open for updateRaindrop.
     */
    private void updateRaindropFallback(
            Long raindropId, Long collectionId, List<String> tags, Throwable throwable) {
        log.atError()
                .setMessage(
                        "Raindrop API circuit breaker fallback triggered for updateRaindrop raindropId={}: {}")
                .addArgument(raindropId)
                .addArgument(throwable.getMessage())
                .log();
        throw new ExternalServiceException(
                "raindrop", "Failed to update raindrop - Raindrop API unavailable", throwable);
    }
}
