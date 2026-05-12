package org.bartram.vidtag.model;

import java.time.Instant;
import java.util.List;

/**
 * One row in the recent-processed-videos view shown at {@code GET /processed}.
 *
 * @param timestamp  when processing finished
 * @param source     which pipeline produced this entry
 * @param title      video title
 * @param url        YouTube watch URL
 * @param status     processing outcome
 * @param tags       flattened AI-suggested tag labels (no confidence scores)
 * @param collection Raindrop collection name, or null if not applicable
 */
public record ProcessedVideoEntry(
        Instant timestamp,
        Source source,
        String title,
        String url,
        ProcessingStatus status,
        List<String> tags,
        String collection) {}
