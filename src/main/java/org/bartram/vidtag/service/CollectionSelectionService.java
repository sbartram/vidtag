package org.bartram.vidtag.service;

import org.bartram.vidtag.config.RaindropProperties;
import org.bartram.vidtag.model.VideoMetadata;
import org.bartram.vidtag.service.YouTubeService.PlaylistMetadata;
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
            String response = chatClient.prompt(prompt).call().content();
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
}
