package org.bartram.vidtag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the playlist processing scheduler.
 */
@Component
@ConfigurationProperties(prefix = "vidtag.scheduler")
public class SchedulerProperties {

    /**
     * Enable or disable the scheduled playlist processor.
     */
    private boolean enabled = true;

    /**
     * Fixed delay between job executions in hours.
     */
    private int fixedDelayHours = 1;

    /**
     * Comma-separated YouTube playlist IDs to process (e.g., PLxxx...,PLyyy...).
     * Playlist IDs can be found in the YouTube URL: https://www.youtube.com/playlist?list=PLxxx...
     * Multiple playlists are processed sequentially. Whitespace is trimmed automatically.
     */
    private String playlistIds = "";

    /**
     * @deprecated Use playlistIds instead. Finding playlists by name requires OAuth authentication.
     * This property is ignored if playlistIds is set.
     */
    @Deprecated(since = "1.0.0", forRemoval = true)
    private String playlistName = "tag";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getFixedDelayHours() {
        return fixedDelayHours;
    }

    public void setFixedDelayHours(int fixedDelayHours) {
        this.fixedDelayHours = fixedDelayHours;
    }

    public String getPlaylistIds() {
        return playlistIds;
    }

    public void setPlaylistIds(String playlistIds) {
        this.playlistIds = playlistIds;
    }

    /**
     * @deprecated Use getPlaylistIds() instead. Finding playlists by name requires OAuth.
     */
    @Deprecated(since = "1.0.0", forRemoval = true)
    public String getPlaylistName() {
        return playlistName;
    }

    /**
     * @deprecated Use setPlaylistIds() instead. Finding playlists by name requires OAuth.
     */
    @Deprecated(since = "1.0.0", forRemoval = true)
    public void setPlaylistName(String playlistName) {
        this.playlistName = playlistName;
    }
}
