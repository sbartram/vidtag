package org.bartram.vidtag.dto;

import jakarta.validation.constraints.NotBlank;
import org.bartram.vidtag.model.TagStrategy;
import org.bartram.vidtag.model.Verbosity;
import org.bartram.vidtag.model.VideoFilters;

/**
 * Request DTO for tagging a YouTube playlist.
 *
 * @param playlistInput YouTube playlist ID or URL
 * @param raindropCollectionTitle title of the Raindrop.io collection to save videos to
 * @param filters optional filters for selecting videos from the playlist
 * @param tagStrategy optional strategy for tag generation
 * @param verbosity verbosity level for processing output
 */
public record TagPlaylistRequest(
    @NotBlank(message = "playlistInput is required")
    String playlistInput,
    @NotBlank(message = "raindropCollectionTitle is required")
    String raindropCollectionTitle,
    VideoFilters filters,
    TagStrategy tagStrategy,
    Verbosity verbosity
) {
}
