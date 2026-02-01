package org.bartram.vidtag.dto;

import jakarta.validation.constraints.NotBlank;
import org.bartram.vidtag.model.TagStrategy;
import org.bartram.vidtag.model.Verbosity;
import org.bartram.vidtag.model.VideoFilters;

/**
 * Request DTO for tagging a YouTube playlist.
 * Collection is automatically determined by AI analysis.
 *
 * @param playlistInput YouTube playlist ID or URL
 * @param filters optional filters for selecting videos from the playlist
 * @param tagStrategy optional strategy for tag generation
 * @param verbosity verbosity level for processing output
 */
public record TagPlaylistRequest(
    @NotBlank(message = "playlistInput is required")
    String playlistInput,
    // raindropCollectionTitle REMOVED - AI determines collection
    VideoFilters filters,
    TagStrategy tagStrategy,
    Verbosity verbosity
) {
}
