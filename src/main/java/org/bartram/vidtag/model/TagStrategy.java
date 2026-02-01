package org.bartram.vidtag.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Strategy configuration for AI-powered tag generation.
 *
 * @param maxTagsPerVideo maximum number of tags to generate per video (optional)
 * @param confidenceThreshold minimum confidence score for tags (optional)
 * @param customInstructions custom instructions for tag generation (optional)
 */
public record TagStrategy(Integer maxTagsPerVideo, Double confidenceThreshold, String customInstructions) {
    /**
     * Default strategy for tag suggestions with reasonable defaults.
     */
    public static final TagStrategy SUGGEST = new TagStrategy(5, 0.5, null);

    /**
     * Factory method for JSON deserialization from string values like "SUGGEST".
     * Allows API users to use predefined strategies by name.
     *
     * @param name the strategy name (case-insensitive)
     * @return the corresponding TagStrategy
     * @throws IllegalArgumentException if the name is not recognized
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static TagStrategy fromString(String name) {
        return switch (name.toUpperCase()) {
            case "SUGGEST" -> SUGGEST;
            default -> throw new IllegalArgumentException("Unknown TagStrategy: " + name);
        };
    }
}
