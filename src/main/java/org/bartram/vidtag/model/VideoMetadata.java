package org.bartram.vidtag.model;

import java.time.Instant;

/**
 * Metadata for a YouTube video.
 *
 * @param videoId YouTube video ID
 * @param url YouTube video URL
 * @param title video title
 * @param description video description (optional)
 * @param publishedAt video publication date (optional)
 * @param duration video duration in seconds (optional)
 */
public record VideoMetadata(
    String videoId,
    String url,
    String title,
    String description,
    Instant publishedAt,
    Integer duration
) {
}
