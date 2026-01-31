package org.bartram.vidtag.model;

import java.time.Instant;

/**
 * Filter criteria for selecting videos from a playlist.
 *
 * @param publishedAfter only include videos published after this date (optional)
 * @param maxDuration maximum video duration in seconds (optional)
 * @param maxVideos maximum number of videos to process (optional)
 */
public record VideoFilters(
    Instant publishedAfter,
    Integer maxDuration,
    Integer maxVideos
) {
}
