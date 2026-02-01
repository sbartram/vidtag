package org.bartram.vidtag.model;

import java.util.List;

/**
 * Result of processing a single video.
 *
 * @param video video metadata
 * @param selectedTags AI-generated tags (empty if skipped or failed)
 * @param status processing status
 * @param errorMessage error message if processing failed
 */
public record VideoProcessingResult(
        VideoMetadata video, List<TagWithConfidence> selectedTags, ProcessingStatus status, String errorMessage) {}
