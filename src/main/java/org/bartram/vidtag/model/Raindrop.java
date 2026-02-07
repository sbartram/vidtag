package org.bartram.vidtag.model;

/**
 * Represents a bookmark item from the Raindrop.io API.
 *
 * @param id Raindrop bookmark ID
 * @param link bookmark URL
 * @param title bookmark title
 */
public record Raindrop(Long id, String link, String title) {}
