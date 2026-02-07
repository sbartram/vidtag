package org.bartram.vidtag.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.vidtag.client.YouTubeApiClient;
import org.bartram.vidtag.exception.ExternalServiceException;
import org.bartram.vidtag.exception.ResourceNotFoundException;
import org.bartram.vidtag.model.VideoFilters;
import org.bartram.vidtag.model.VideoMetadata;
import org.springframework.stereotype.Service;

/**
 * Service for interacting with YouTube API to fetch playlist videos.
 * Includes circuit breaker protection and retry logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YouTubeService {

    private static final Pattern PLAYLIST_ID_PATTERN = Pattern.compile("(?:list=)([a-zA-Z0-9_-]+)");
    private static final Pattern VIDEO_ID_WATCH_PATTERN = Pattern.compile("(?:youtube\\.com/watch\\?.*v=)([a-zA-Z0-9_-]{11})");
    private static final Pattern VIDEO_ID_SHORT_PATTERN = Pattern.compile("(?:youtu\\.be/)([a-zA-Z0-9_-]{11})");

    private final YouTubeApiClient youtubeApiClient;

    /**
     * Extracts playlist ID from a YouTube URL or returns the input if it's already an ID.
     *
     * @param playlistUrlOrId YouTube playlist URL or playlist ID
     * @return extracted playlist ID
     */
    public String extractPlaylistId(String playlistUrlOrId) {
        Matcher matcher = PLAYLIST_ID_PATTERN.matcher(playlistUrlOrId);
        if (matcher.find()) {
            return matcher.group(1);
        }
        // If no match found, assume the input is already a playlist ID
        return playlistUrlOrId;
    }

    /**
     * Fetches and filters videos from a YouTube playlist.
     * Protected by circuit breaker and retry logic with fallback that throws RuntimeException.
     *
     * @param playlistId the YouTube playlist ID
     * @param filters optional filters to apply (publishedAfter, maxDuration, maxVideos)
     * @return filtered list of video metadata
     * @throws RuntimeException if all retries fail and circuit breaker is triggered
     */
    @Retry(name = "youtube")
    @CircuitBreaker(name = "youtube", fallbackMethod = "fetchPlaylistVideosFallback")
    public List<VideoMetadata> fetchPlaylistVideos(String playlistId, VideoFilters filters) {
        log.atDebug()
                .setMessage("Fetching playlist videos for playlistId={}")
                .addArgument(playlistId)
                .log();

        List<VideoMetadata> videos = youtubeApiClient.getPlaylistVideos(playlistId);

        // Apply filters using stream operations
        Stream<VideoMetadata> videoStream = videos.stream();

        // Filter by publishedAfter
        if (filters != null && filters.publishedAfter() != null) {
            videoStream = videoStream.filter(
                    video -> video.publishedAt() != null && video.publishedAt().isAfter(filters.publishedAfter()));
        }

        // Filter by maxDuration
        if (filters != null && filters.maxDuration() != null) {
            videoStream =
                    videoStream.filter(video -> video.duration() != null && video.duration() <= filters.maxDuration());
        }

        // Limit by maxVideos
        if (filters != null && filters.maxVideos() != null) {
            videoStream = videoStream.limit(filters.maxVideos());
        }

        List<VideoMetadata> filteredVideos = videoStream.toList();
        log.atInfo()
                .setMessage("Fetched {} videos from playlist {} (filtered from {})")
                .addArgument(filteredVideos.size())
                .addArgument(playlistId)
                .addArgument(videos.size())
                .log();

        return filteredVideos;
    }

    /**
     * Fallback method when YouTube API circuit breaker is open.
     *
     * @param playlistId the playlist ID that was attempted
     * @param filters the filters that were applied
     * @param throwable the exception that triggered the fallback
     * @throws ExternalServiceException always thrown with descriptive message
     */
    private List<VideoMetadata> fetchPlaylistVideosFallback(
            String playlistId, VideoFilters filters, Throwable throwable) {
        log.atError()
                .setMessage("YouTube API circuit breaker fallback triggered for playlist {}: {}")
                .addArgument(playlistId)
                .addArgument(throwable.getMessage())
                .log();
        throw new ExternalServiceException("youtube", "YouTube API is currently unavailable", throwable);
    }

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
        log.atDebug()
                .setMessage("Searching for playlist with name: {}")
                .addArgument(playlistName)
                .log();

        String playlistId = youtubeApiClient.findPlaylistByName(playlistName);

        if (playlistId == null) {
            log.atWarn()
                    .setMessage("Playlist not found: {}")
                    .addArgument(playlistName)
                    .log();
            throw new ResourceNotFoundException("Playlist not found: " + playlistName);
        }

        log.atInfo()
                .setMessage("Found playlist '{}' with ID: {}")
                .addArgument(playlistName)
                .addArgument(playlistId)
                .log();
        return playlistId;
    }

    /**
     * Fallback method when YouTube API circuit breaker is open during playlist search.
     */
    private String findPlaylistByNameFallback(String playlistName, Throwable throwable) {
        log.atError()
                .setMessage("YouTube API circuit breaker fallback triggered for playlist search '{}': {}")
                .addArgument(playlistName)
                .addArgument(throwable.getMessage())
                .log();
        throw new ExternalServiceException("youtube", "YouTube API is currently unavailable", throwable);
    }

    /**
     * Get playlist metadata (title and description).
     * For now, returns basic metadata based on playlist ID.
     * Can be enhanced later to fetch actual metadata from YouTube API.
     *
     * @param playlistId playlist ID
     * @return playlist metadata
     */
    public PlaylistMetadata getPlaylistMetadata(String playlistId) {
        log.atDebug()
                .setMessage("Fetching metadata for playlist: {}")
                .addArgument(playlistId)
                .log();
        // For now, return basic metadata - can be enhanced later with actual API call
        return new PlaylistMetadata(playlistId, "");
    }

    /**
     * Get videos from a playlist with a maximum count limit.
     * Convenience method that wraps fetchPlaylistVideos with simple maxVideos filter.
     *
     * @param playlistId playlist ID
     * @param maxVideos maximum number of videos to return
     * @return list of video metadata
     */
    public List<VideoMetadata> getPlaylistVideos(String playlistId, int maxVideos) {
        log.atDebug()
                .setMessage("Fetching up to {} videos from playlist: {}")
                .addArgument(maxVideos)
                .addArgument(playlistId)
                .log();
        VideoFilters filters = new VideoFilters(null, null, maxVideos);
        return fetchPlaylistVideos(playlistId, filters);
    }

    /**
     * Fetches metadata for a single video by ID.
     * Protected by circuit breaker and retry logic.
     *
     * @param videoId the YouTube video ID
     * @return video metadata, or null if not found
     */
    @Retry(name = "youtube")
    @CircuitBreaker(name = "youtube", fallbackMethod = "getVideoMetadataFallback")
    public VideoMetadata getVideoMetadata(String videoId) {
        log.atDebug()
                .setMessage("Fetching metadata for video: {}")
                .addArgument(videoId)
                .log();
        return youtubeApiClient.getVideo(videoId);
    }

    /**
     * Extracts a YouTube video ID from a URL.
     * Supports youtube.com/watch?v=XXX and youtu.be/XXX formats.
     *
     * @param url the URL to parse
     * @return the video ID, or null if not a YouTube URL
     */
    public String extractVideoId(String url) {
        if (url == null) {
            return null;
        }
        Matcher watchMatcher = VIDEO_ID_WATCH_PATTERN.matcher(url);
        if (watchMatcher.find()) {
            return watchMatcher.group(1);
        }
        Matcher shortMatcher = VIDEO_ID_SHORT_PATTERN.matcher(url);
        if (shortMatcher.find()) {
            return shortMatcher.group(1);
        }
        return null;
    }

    /**
     * Fallback method when YouTube API circuit breaker is open during video metadata fetch.
     */
    private VideoMetadata getVideoMetadataFallback(String videoId, Throwable throwable) {
        log.atError()
                .setMessage("YouTube API circuit breaker fallback triggered for video {}: {}")
                .addArgument(videoId)
                .addArgument(throwable.getMessage())
                .log();
        throw new ExternalServiceException("youtube", "YouTube API is currently unavailable", throwable);
    }

    /**
     * Simple record for playlist metadata.
     */
    public record PlaylistMetadata(String title, String description) {}
}
