package org.bartram.vidtag.client;

import org.bartram.vidtag.model.VideoMetadata;

import java.util.List;

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
}
