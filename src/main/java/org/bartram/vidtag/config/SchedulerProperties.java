package org.bartram.vidtag.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the playlist processing scheduler.
 */
@Getter
@Setter
@Accessors(fluent = false)
@Component
@ConfigurationProperties(prefix = "vidtag.scheduler")
public class SchedulerProperties {

    /**
     * Enable or disable the scheduled playlist processor.
     */
    private boolean enabled = true;

    /**
     * Fixed delay between job executions.
     */
    private Duration fixedDelay = Duration.ofHours(1);

    /**
     * Initial delay before the first execution.
     */
    private Duration initialDelay = Duration.ofSeconds(10);

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
}
