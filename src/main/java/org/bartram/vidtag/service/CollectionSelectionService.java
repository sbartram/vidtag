package org.bartram.vidtag.service;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bartram.vidtag.config.RaindropProperties;
import org.bartram.vidtag.model.VideoMetadata;
import org.bartram.vidtag.service.YouTubeService.PlaylistMetadata;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/**
 * Service for AI-powered collection selection.
 * Analyzes playlist content and determines the most appropriate Raindrop collection.
 */
@Slf4j
@Service
public class CollectionSelectionService {

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
            ChatClient.Builder chatClientBuilder,
            RaindropProperties raindropProperties,
            CacheManager cacheManager) {
        this.raindropService = raindropService;
        this.youtubeService = youtubeService;
        this.chatClient = chatClientBuilder.build();
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
        log.atDebug()
                .setMessage("Selecting collection for playlist: {}")
                .addArgument(playlistId)
                .log();

        // Check cache first
        String cached = getCachedCollection(playlistId);
        if (cached != null) {
            log.atDebug()
                    .setMessage("Using cached collection '{}' for playlist {}")
                    .addArgument(cached)
                    .addArgument(playlistId)
                    .log();
            return cached;
        }

        // Get available collections
        List<String> availableCollections = raindropService.getUserCollections();
        if (availableCollections.isEmpty()) {
            log.atWarn().setMessage("No collections available, using fallback").log();
            return ensureFallbackExists(availableCollections);
        }

        // Get playlist metadata and sample videos
        PlaylistMetadata metadata = youtubeService.getPlaylistMetadata(playlistId);
        List<VideoMetadata> sampleVideos = youtubeService.getPlaylistVideos(playlistId, SAMPLE_VIDEO_COUNT);

        if (sampleVideos.isEmpty()) {
            log.atInfo()
                    .setMessage("Empty playlist '{}', using fallback collection")
                    .addArgument(playlistId)
                    .log();
            return ensureFallbackExists(availableCollections);
        }

        // Ask AI to choose collection
        String aiChoice = askAIForCollection(availableCollections, metadata, sampleVideos);
        String selectedCollection = validateAndSelectCollection(aiChoice, availableCollections);

        // Cache the decision
        cacheCollection(playlistId, selectedCollection);

        return selectedCollection;
    }

    /**
     * Select the most appropriate collection for a single video.
     * Uses AI analysis of the video title and description.
     *
     * @param video the video metadata to analyze
     * @return collection title
     */
    public String selectCollectionForVideo(VideoMetadata video) {
        log.atDebug()
                .setMessage("Selecting collection for video: {}")
                .addArgument(video.title())
                .log();

        List<String> availableCollections = raindropService.getUserCollections();
        if (availableCollections.isEmpty()) {
            log.atWarn().setMessage("No collections available, using fallback").log();
            return ensureFallbackExists(availableCollections);
        }

        String aiChoice = askAIForVideoCollection(availableCollections, video);
        return validateAndSelectCollection(aiChoice, availableCollections);
    }

    private String askAIForVideoCollection(List<String> availableCollections, VideoMetadata video) {
        String prompt = buildVideoPrompt(availableCollections, video);
        log.atDebug().setMessage("Asking AI to select collection for video").log();

        try {
            String response = chatClient.prompt(prompt).call().content();
            return response.trim();
        } catch (Exception e) {
            log.atError()
                    .setMessage("Failed to get AI response for video collection: {}")
                    .addArgument(e.getMessage())
                    .setCause(e)
                    .log();
            return LOW_CONFIDENCE;
        }
    }

    private String buildVideoPrompt(List<String> availableCollections, VideoMetadata video) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are helping categorize a YouTube video into a Raindrop.io collection.\n\n");

        prompt.append("Available collections:\n");
        for (String collection : availableCollections) {
            prompt.append("- ").append(collection).append("\n");
        }

        prompt.append("\nVideo information:\n");
        prompt.append("Title: ").append(video.title()).append("\n");
        if (video.description() != null && !video.description().isBlank()) {
            prompt.append("Description: ").append(video.description()).append("\n");
        }

        prompt.append(
                "\nChoose the most appropriate collection from the available collections above for this video.\n\n");
        prompt.append("Rules:\n");
        prompt.append("- Respond with ONLY the exact collection name from the list\n");
        prompt.append("- If none of the collections are a good fit, respond with exactly \"LOW_CONFIDENCE\"\n");
        prompt.append("- Do not create new collection names\n");
        prompt.append("- Do not explain your reasoning\n");
        prompt.append("- Match the collection name exactly as shown in the list\n\n");
        prompt.append("Response:");

        return prompt.toString();
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
            log.atDebug()
                    .setMessage("Cached collection '{}' for playlist {}")
                    .addArgument(collection)
                    .addArgument(playlistId)
                    .log();
        }
    }

    private String askAIForCollection(
            List<String> availableCollections, PlaylistMetadata metadata, List<VideoMetadata> sampleVideos) {

        String prompt = buildPrompt(availableCollections, metadata, sampleVideos);
        log.atDebug().setMessage("Asking AI to select collection").log();

        try {
            String response = chatClient.prompt(prompt).call().content();
            return response.trim();
        } catch (Exception e) {
            log.atError()
                    .setMessage("Failed to get AI response: {}")
                    .addArgument(e.getMessage())
                    .setCause(e)
                    .log();
            return LOW_CONFIDENCE;
        }
    }

    private String buildPrompt(
            List<String> availableCollections, PlaylistMetadata metadata, List<VideoMetadata> sampleVideos) {

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

        prompt.append(
                "\nChoose the most appropriate collection from the available collections above for this playlist.\n\n");
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
            log.atInfo()
                    .setMessage("AI indicated low confidence, using fallback collection")
                    .log();
            return ensureFallbackExists(availableCollections);
        }

        if (availableCollections.contains(aiChoice)) {
            log.atInfo()
                    .setMessage("AI selected collection: {}")
                    .addArgument(aiChoice)
                    .log();
            return aiChoice;
        }

        log.atWarn()
                .setMessage("AI suggested non-existent collection '{}', using fallback")
                .addArgument(aiChoice)
                .log();
        return ensureFallbackExists(availableCollections);
    }

    private String ensureFallbackExists(List<String> availableCollections) {
        String fallback = raindropProperties.getFallbackCollection();

        if (!availableCollections.contains(fallback)) {
            log.atInfo()
                    .setMessage("Fallback collection '{}' does not exist, creating it")
                    .addArgument(fallback)
                    .log();
            raindropService.createCollection(fallback);
        }

        return fallback;
    }
}
