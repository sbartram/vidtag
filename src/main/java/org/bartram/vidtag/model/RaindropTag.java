package org.bartram.vidtag.model;

import java.io.Serializable;

/**
 * Represents a tag in Raindrop.io.
 * Implements Serializable for Redis caching.
 *
 * @param name the tag name
 */
public record RaindropTag(
    String name
) implements Serializable {
}
