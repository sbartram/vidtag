package org.bartram.vidtag.client;

import java.util.List;
import org.bartram.vidtag.model.Raindrop;
import org.bartram.vidtag.model.RaindropCollection;
import org.bartram.vidtag.model.RaindropTag;

/**
 * Client interface for interacting with Raindrop.io API.
 * Implementation should handle API communication and error handling.
 */
public interface RaindropApiClient {

    /**
     * Fetches all tags for a user.
     *
     * @param userId the Raindrop user ID
     * @return list of all user tags
     * @throws RuntimeException if API call fails
     */
    List<RaindropTag> getUserTags(String userId);

    /**
     * Fetches all collections for a user.
     *
     * @param userId the Raindrop user ID
     * @return list of all user collections
     * @throws RuntimeException if API call fails
     */
    List<RaindropCollection> getUserCollections(String userId);

    /**
     * Checks if a bookmark already exists in a collection.
     *
     * @param collectionId the collection ID
     * @param url the bookmark URL to check
     * @return true if bookmark exists, false otherwise
     * @throws RuntimeException if API call fails
     */
    boolean bookmarkExists(Long collectionId, String url);

    /**
     * Creates a new bookmark in a collection.
     *
     * @param collectionId the collection ID
     * @param url the bookmark URL
     * @param title the bookmark title
     * @param tags list of tag names to apply to the bookmark
     * @throws RuntimeException if API call fails
     */
    void createBookmark(Long collectionId, String url, String title, List<String> tags);

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

    /**
     * Fetches all bookmarks (raindrops) in a collection.
     *
     * @param collectionId the collection ID (-1 for Unsorted)
     * @return list of raindrops in the collection
     * @throws RuntimeException if API call fails
     */
    List<Raindrop> getRaindrops(Long collectionId);

    /**
     * Updates a raindrop's collection and tags.
     *
     * @param raindropId the raindrop ID to update
     * @param collectionId the target collection ID
     * @param tags list of tag names to apply
     * @throws RuntimeException if API call fails
     */
    void updateRaindrop(Long raindropId, Long collectionId, List<String> tags);
}
