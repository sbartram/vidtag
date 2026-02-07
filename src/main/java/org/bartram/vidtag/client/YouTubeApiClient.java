package org.bartram.vidtag.client;

import java.util.List;
import org.bartram.vidtag.model.VideoMetadata;

/**
 * Client interface for interacting with YouTube Data API.
 * Implementation should handle API communication and error handling.
 */
public interface YouTubeApiClient {

    /**
     * Fetches all videos from a YouTube playlist.
     *
     * @param playlistId the YouTube playlist ID
     * @return list of video metadata for all videos in the playlist
     * @throws RuntimeException if API call fails
     */
    List<VideoMetadata> getPlaylistVideos(String playlistId);

    /**
     * Finds a playlist by its name.
     *
     * @param playlistName the name of the playlist to find
     * @return the playlist ID if found, null otherwise
     */
    String findPlaylistByName(String playlistName);

    /**
     * Fetches metadata for a single video by ID.
     *
     * @param videoId the YouTube video ID
     * @return video metadata, or null if not found
     * @throws RuntimeException if API call fails
     */
    VideoMetadata getVideo(String videoId);
}
