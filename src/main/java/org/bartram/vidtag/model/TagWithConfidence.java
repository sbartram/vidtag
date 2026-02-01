package org.bartram.vidtag.model;

/**
 * AI-generated tag with confidence score.
 *
 * @param tag the tag text
 * @param confidence confidence score (0.0 to 1.0)
 * @param isExisting whether this tag already exists on the video
 */
public record TagWithConfidence(String tag, Double confidence, Boolean isExisting) {}
