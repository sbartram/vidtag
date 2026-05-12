package org.bartram.vidtag.model;

/**
 * Which pipeline produced a {@link ProcessedVideoEntry}.
 */
public enum Source {
    /** Playlist tagging pipeline (YouTube playlists → Raindrop). */
    PLAYLIST,
    /** Unsorted bookmark processor (Raindrop Unsorted → tagged + moved). */
    UNSORTED
}
