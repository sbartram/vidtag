package org.bartram.vidtag.client.impl;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.PlaylistListResponse;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bartram.vidtag.client.YouTubeApiClient;
import org.bartram.vidtag.exception.ExternalServiceException;
import org.bartram.vidtag.model.VideoMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implementation of YouTubeApiClient using Google's YouTube Data API v3.
 * Only activated when youtube.api.key property is set.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "youtube.api.key")
public class YouTubeApiClientImpl implements YouTubeApiClient {

    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String APPLICATION_NAME = "VidTag";
    private static final long MAX_RESULTS = 50L;

    private final YouTube youtubeService;
    private final String apiKey;

    @Autowired
    public YouTubeApiClientImpl(@Value("${youtube.api.key}") String apiKey) {
        this.apiKey = apiKey;
        this.youtubeService = createYouTubeService();
    }

    // Package-private constructor for testing
    YouTubeApiClientImpl(YouTube youtubeService, String apiKey) {
        this.youtubeService = youtubeService;
        this.apiKey = apiKey;
    }

    private YouTube createYouTubeService() {
        try {
            final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            return new YouTube.Builder(httpTransport, JSON_FACTORY, null)
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        } catch (GeneralSecurityException | IOException e) {
            log.atError()
                    .setMessage("Failed to create YouTube service")
                    .setCause(e)
                    .log();
            throw new RuntimeException("Failed to initialize YouTube API client", e);
        }
    }

    @Override
    public List<VideoMetadata> getPlaylistVideos(String playlistId) {
        log.atDebug()
                .setMessage("Fetching videos from playlist: {}")
                .addArgument(playlistId)
                .log();

        try {
            List<VideoMetadata> allVideos = new ArrayList<>();
            String nextPageToken = null;

            do {
                PlaylistItemListResponse response = youtubeService
                        .playlistItems()
                        .list(List.of("snippet", "contentDetails"))
                        .setPlaylistId(playlistId)
                        .setMaxResults(MAX_RESULTS)
                        .setPageToken(nextPageToken)
                        .setKey(getApiKey())
                        .execute();

                response.getItems().forEach(item -> {
                    String videoId = item.getContentDetails().getVideoId();
                    String url = "https://www.youtube.com/watch?v=" + videoId;
                    String title = item.getSnippet().getTitle();
                    String description = item.getSnippet().getDescription();

                    Instant publishedAt =
                            Instant.parse(item.getSnippet().getPublishedAt().toString());

                    // Get video duration from separate API call
                    Integer duration = getVideoDuration(videoId);

                    allVideos.add(new VideoMetadata(videoId, url, title, description, publishedAt, duration));
                });

                nextPageToken = response.getNextPageToken();
            } while (nextPageToken != null);

            log.atInfo()
                    .setMessage("Fetched {} videos from playlist {}")
                    .addArgument(allVideos.size())
                    .addArgument(playlistId)
                    .log();
            return allVideos;

        } catch (IOException e) {
            log.atError()
                    .setMessage("Failed to fetch playlist videos: {}")
                    .addArgument(playlistId)
                    .setCause(e)
                    .log();
            throw new RuntimeException("Failed to fetch YouTube playlist", e);
        }
    }

    @Override
    public VideoMetadata getVideo(String videoId) {
        log.atDebug()
                .setMessage("Fetching video metadata for: {}")
                .addArgument(videoId)
                .log();

        try {
            var response = youtubeService
                    .videos()
                    .list(List.of("snippet", "contentDetails"))
                    .setId(List.of(videoId))
                    .setKey(getApiKey())
                    .execute();

            if (response.getItems() == null || response.getItems().isEmpty()) {
                log.atWarn()
                        .setMessage("Video not found: {}")
                        .addArgument(videoId)
                        .log();
                return null;
            }

            var item = response.getItems().get(0);
            String url = "https://www.youtube.com/watch?v=" + videoId;
            String title = item.getSnippet().getTitle();
            String description = item.getSnippet().getDescription();
            Instant publishedAt =
                    Instant.parse(item.getSnippet().getPublishedAt().toString());
            String durationStr = item.getContentDetails().getDuration();
            Integer duration = parseDuration(durationStr);

            VideoMetadata metadata = new VideoMetadata(videoId, url, title, description, publishedAt, duration);
            log.atInfo()
                    .setMessage("Fetched metadata for video: {}")
                    .addArgument(title)
                    .log();
            return metadata;

        } catch (IOException e) {
            log.atError()
                    .setMessage("Failed to fetch video metadata: {}")
                    .addArgument(videoId)
                    .setCause(e)
                    .log();
            throw new RuntimeException("Failed to fetch video metadata", e);
        }
    }

    private Integer getVideoDuration(String videoId) {
        try {
            var response = youtubeService
                    .videos()
                    .list(List.of("contentDetails"))
                    .setId(List.of(videoId))
                    .setKey(getApiKey())
                    .execute();

            if (response.getItems().isEmpty()) {
                return null;
            }

            String duration = response.getItems().get(0).getContentDetails().getDuration();
            return parseDuration(duration);

        } catch (IOException e) {
            log.atWarn()
                    .setMessage("Failed to fetch duration for video: {}")
                    .addArgument(videoId)
                    .setCause(e)
                    .log();
            return null;
        }
    }

    /**
     * Parses ISO 8601 duration format (e.g., PT15M33S) to seconds.
     */
    private Integer parseDuration(String isoDuration) {
        if (isoDuration == null || !isoDuration.startsWith("PT")) {
            return null;
        }

        try {
            java.time.Duration duration = java.time.Duration.parse(isoDuration);
            return (int) duration.getSeconds();
        } catch (Exception e) {
            log.atWarn()
                    .setMessage("Failed to parse duration: {}")
                    .addArgument(isoDuration)
                    .setCause(e)
                    .log();
            return null;
        }
    }

    private String getApiKey() {
        return this.apiKey;
    }

    /**
     * Find a playlist by name.
     *
     * @deprecated This method requires OAuth 2.0 authentication and will fail with API key only.
     *             Use playlist IDs directly instead. Playlist IDs can be found in YouTube URLs:
     *             https://www.youtube.com/playlist?list=PLxxx...
     *             OAuth support is planned for a future release.
     * @param playlistName the name of the playlist to search for
     * @return the playlist ID if found, null otherwise
     * @throws ExternalServiceException if the API call fails (including OAuth errors)
     */
    @Deprecated(since = "1.0.0")
    @Override
    public String findPlaylistByName(String playlistName) {
        try {
            log.atDebug()
                    .setMessage("Searching for playlist with name: {}")
                    .addArgument(playlistName)
                    .log();
            log.atWarn()
                    .setMessage(
                            "findPlaylistByName requires OAuth 2.0 authentication. This will fail with API key only. Use playlist IDs directly instead.")
                    .log();

            YouTube.Playlists.List request = youtubeService
                    .playlists()
                    .list(List.of("snippet"))
                    .setMine(true) // Requires OAuth - will fail with API key only
                    .setMaxResults(50L);

            PlaylistListResponse response = request.execute();
            List<Playlist> playlists = response.getItems();

            if (playlists == null || playlists.isEmpty()) {
                log.atDebug().setMessage("No playlists found").log();
                return null;
            }

            // Search for playlist with matching title (case-insensitive)
            for (Playlist playlist : playlists) {
                String title = playlist.getSnippet().getTitle();
                if (title != null && title.equalsIgnoreCase(playlistName)) {
                    log.atDebug()
                            .setMessage("Found playlist '{}' with ID: {}")
                            .addArgument(title)
                            .addArgument(playlist.getId())
                            .log();
                    return playlist.getId();
                }
            }

            log.atDebug()
                    .setMessage("Playlist '{}' not found in {} playlists")
                    .addArgument(playlistName)
                    .addArgument(playlists.size())
                    .log();
            return null;

        } catch (IOException e) {
            log.atError()
                    .setMessage("Failed to search for playlist '{}': {}")
                    .addArgument(playlistName)
                    .addArgument(e.getMessage())
                    .setCause(e)
                    .log();
            throw new ExternalServiceException(
                    "youtube",
                    String.format("Failed to search for playlist '%s': %s", playlistName, e.getMessage()),
                    e);
        }
    }
}
