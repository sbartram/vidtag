package org.bartram.vidtag.client.impl;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.PlaylistListResponse;
import org.bartram.vidtag.client.YouTubeApiClient;
import org.bartram.vidtag.exception.ExternalServiceException;
import org.bartram.vidtag.model.VideoMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of YouTubeApiClient using Google's YouTube Data API v3.
 * Only activated when youtube.api.key property is set.
 */
@Component
@ConditionalOnProperty(name = "youtube.api.key")
public class YouTubeApiClientImpl implements YouTubeApiClient {

    private static final Logger log = LoggerFactory.getLogger(YouTubeApiClientImpl.class);
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
            log.error("Failed to create YouTube service", e);
            throw new RuntimeException("Failed to initialize YouTube API client", e);
        }
    }

    @Override
    public List<VideoMetadata> getPlaylistVideos(String playlistId) {
        log.debug("Fetching videos from playlist: {}", playlistId);

        try {
            List<VideoMetadata> allVideos = new ArrayList<>();
            String nextPageToken = null;

            do {
                PlaylistItemListResponse response = youtubeService.playlistItems()
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

                    Instant publishedAt = Instant.parse(item.getSnippet().getPublishedAt().toString());

                    // Get video duration from separate API call
                    Integer duration = getVideoDuration(videoId);

                    allVideos.add(new VideoMetadata(
                            videoId,
                            url,
                            title,
                            description,
                            publishedAt,
                            duration
                    ));
                });

                nextPageToken = response.getNextPageToken();
            } while (nextPageToken != null);

            log.info("Fetched {} videos from playlist {}", allVideos.size(), playlistId);
            return allVideos;

        } catch (IOException e) {
            log.error("Failed to fetch playlist videos: {}", playlistId, e);
            throw new RuntimeException("Failed to fetch YouTube playlist", e);
        }
    }

    private Integer getVideoDuration(String videoId) {
        try {
            var response = youtubeService.videos()
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
            log.warn("Failed to fetch duration for video: {}", videoId, e);
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
            log.warn("Failed to parse duration: {}", isoDuration, e);
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
            log.debug("Searching for playlist with name: {}", playlistName);
            log.warn("findPlaylistByName requires OAuth 2.0 authentication. " +
                    "This will fail with API key only. Use playlist IDs directly instead.");

            YouTube.Playlists.List request = youtubeService.playlists()
                .list(List.of("snippet"))
                .setMine(true)  // Requires OAuth - will fail with API key only
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
}
