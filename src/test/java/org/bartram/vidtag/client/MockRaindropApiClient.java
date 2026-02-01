package org.bartram.vidtag.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bartram.vidtag.model.RaindropCollection;
import org.bartram.vidtag.model.RaindropTag;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration providing a mock RaindropApiClient for integration tests.
 * Tracks saved bookmarks in memory for verification during tests.
 */
@TestConfiguration
public class MockRaindropApiClient {

    private final Set<String> savedUrls = new HashSet<>();

    /**
     * Returns the set of URLs that have been saved via createBookmark.
     * Useful for test assertions.
     *
     * @return set of saved bookmark URLs
     */
    public Set<String> getSavedUrls() {
        return savedUrls;
    }

    /**
     * Clears the saved URLs set. Call this in test setup for clean state.
     */
    public void clearSavedUrls() {
        savedUrls.clear();
    }

    /**
     * Provides a mock RaindropApiClient that returns fixed test data.
     *
     * @return mock RaindropApiClient implementation
     */
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
                        new RaindropTag("programming"));
            }

            @Override
            public List<RaindropCollection> getUserCollections(String userId) {
                return List.of(new RaindropCollection(123L, "My Videos"), new RaindropCollection(456L, "Work"));
            }

            @Override
            public boolean bookmarkExists(Long collectionId, String url) {
                return savedUrls.contains(url);
            }

            @Override
            public void createBookmark(Long collectionId, String url, String title, List<String> tags) {
                savedUrls.add(url);
            }

            @Override
            public List<RaindropCollection> getCollections() {
                return List.of(new RaindropCollection(123L, "My Videos"), new RaindropCollection(456L, "Work"));
            }

            @Override
            public Long createCollection(String title) {
                return 999L; // Return dummy ID for tests
            }
        };
    }
}
