package org.bartram.vidtag.model;

/**
 * Strategy configuration for AI-powered tag generation.
 *
 * @param maxTagsPerVideo maximum number of tags to generate per video (optional)
 * @param confidenceThreshold minimum confidence score for tags (optional)
 * @param customInstructions custom instructions for tag generation (optional)
 */
public record TagStrategy(
    Integer maxTagsPerVideo,
    Double confidenceThreshold,
    String customInstructions
) {
    /**
     * Default strategy for tag suggestions with reasonable defaults.
     */
    public static final TagStrategy SUGGEST = new TagStrategy(5, 0.5, null);
}
