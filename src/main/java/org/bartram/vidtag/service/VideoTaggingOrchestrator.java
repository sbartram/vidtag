package org.bartram.vidtag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.vidtag.dto.TagPlaylistRequest;
import org.bartram.vidtag.event.ProgressEvent;
import org.bartram.vidtag.model.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Orchestrates the video tagging workflow: YouTube -> Raindrop collection resolution -> AI tagging -> bookmark saving.
 * Processes videos in batches and emits SSE progress events throughout the workflow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoTaggingOrchestrator {

    private static final int BATCH_SIZE = 10;
    private static final String DEFAULT_USER_ID = "default"; // TODO: Replace with auth context later

    private final YouTubeService youtubeService;
    private final RaindropService raindropService;
    private final VideoTaggingService videoTaggingService;
    private final CollectionSelectionService collectionSelectionService;

    /**
     * Processes a YouTube playlist asynchronously, tagging videos and saving them to Raindrop.
     * Emits progress events throughout the workflow for SSE streaming.
     *
     * @param request the playlist processing request
     * @param eventEmitter consumer that receives progress events for SSE streaming
     */
    @Async
    public void processPlaylist(TagPlaylistRequest request, Consumer<ProgressEvent> eventEmitter) {
        log.info("Starting playlist processing for: {}", request.playlistInput());

        int succeeded = 0;
        int skipped = 0;
        int failed = 0;
        int totalVideos = 0;

        try {
            // Emit started event
            eventEmitter.accept(ProgressEvent.started(
                String.format("Processing playlist: %s", request.playlistInput())
            ));

            // Extract playlist ID
            String playlistId = youtubeService.extractPlaylistId(request.playlistInput());
            log.debug("Extracted playlist ID: {}", playlistId);

            // AI determines collection
            eventEmitter.accept(ProgressEvent.progress("Analyzing playlist to determine collection"));
            String collectionTitle = collectionSelectionService.selectCollection(playlistId);
            log.info("AI selected collection '{}' for playlist {}", collectionTitle, playlistId);

            eventEmitter.accept(ProgressEvent.progress(
                String.format("Resolving collection: %s", collectionTitle)
            ));

            Long collectionId = raindropService.resolveCollectionId(DEFAULT_USER_ID, collectionTitle);
            if (collectionId == null) {
                log.warn("Collection not found: {}", collectionTitle);
                eventEmitter.accept(ProgressEvent.error(
                    String.format("Collection not found: %s", collectionTitle)
                ));
                eventEmitter.accept(ProgressEvent.completed(
                    "Processing completed with errors",
                    new ProcessingSummary(0, 0, 0, 0)
                ));
                return;
            }
            log.debug("Resolved collection ID: {}", collectionId);

            // Fetch existing Raindrop tags (cached)
            eventEmitter.accept(ProgressEvent.progress("Fetching existing tags"));
            List<RaindropTag> existingTags = raindropService.getUserTags(DEFAULT_USER_ID);
            log.debug("Fetched {} existing tags", existingTags.size());

            // Fetch YouTube videos with filters
            eventEmitter.accept(ProgressEvent.progress("Fetching videos from playlist"));
            List<VideoMetadata> videos = youtubeService.fetchPlaylistVideos(playlistId, request.filters());
            totalVideos = videos.size();
            log.info("Fetched {} videos from playlist {}", totalVideos, playlistId);

            eventEmitter.accept(ProgressEvent.progress(
                String.format("Found %d videos to process", totalVideos)
            ));

            if (videos.isEmpty()) {
                eventEmitter.accept(ProgressEvent.completed(
                    "No videos to process",
                    new ProcessingSummary(0, 0, 0, 0)
                ));
                return;
            }

            // Process videos in batches
            List<List<VideoMetadata>> batches = partition(videos, BATCH_SIZE);
            int batchNumber = 0;

            for (List<VideoMetadata> batch : batches) {
                batchNumber++;
                int batchSucceeded = 0;
                int batchSkipped = 0;
                int batchFailed = 0;

                log.debug("Processing batch {}/{} ({} videos)", batchNumber, batches.size(), batch.size());

                for (VideoMetadata video : batch) {
                    VideoProcessingResult result = processVideo(
                        video, collectionId, existingTags, request.tagStrategy(), eventEmitter
                    );

                    switch (result.status()) {
                        case SUCCESS -> {
                            succeeded++;
                            batchSucceeded++;
                        }
                        case SKIPPED -> {
                            skipped++;
                            batchSkipped++;
                        }
                        case FAILED -> {
                            failed++;
                            batchFailed++;
                        }
                    }
                }

                // Emit batch completed event
                eventEmitter.accept(ProgressEvent.batchCompleted(
                    String.format("Batch %d/%d completed: %d succeeded, %d skipped, %d failed",
                        batchNumber, batches.size(), batchSucceeded, batchSkipped, batchFailed),
                    new BatchStats(batchNumber, batches.size(), batchSucceeded, batchSkipped, batchFailed)
                ));
            }

        } catch (Exception e) {
            log.error("Fatal error during playlist processing: {}", e.getMessage(), e);
            eventEmitter.accept(ProgressEvent.error("Fatal error: " + e.getMessage()));
        }

        // Build and emit final summary
        ProcessingSummary summary = buildSummary(totalVideos, succeeded, skipped, failed);
        eventEmitter.accept(ProgressEvent.completed(
            String.format("Processing complete: %d succeeded, %d skipped, %d failed",
                succeeded, skipped, failed),
            summary
        ));

        log.info("Playlist processing completed: {}", summary);
    }

    /**
     * Processes a single video: checks for duplicates, generates tags, saves bookmark.
     *
     * @param video the video to process
     * @param collectionId the Raindrop collection ID
     * @param existingTags existing Raindrop tags for tag suggestion
     * @param tagStrategy tag generation strategy
     * @param eventEmitter consumer for progress events
     * @return the processing result
     */
    private VideoProcessingResult processVideo(
            VideoMetadata video,
            Long collectionId,
            List<RaindropTag> existingTags,
            TagStrategy tagStrategy,
            Consumer<ProgressEvent> eventEmitter) {

        log.debug("Processing video: {} - {}", video.videoId(), video.title());

        try {
            // Check if bookmark already exists
            if (raindropService.bookmarkExists(collectionId, video.url())) {
                log.debug("Video already exists in collection, skipping: {}", video.videoId());

                VideoProcessingResult result = new VideoProcessingResult(
                    video,
                    Collections.emptyList(),
                    ProcessingStatus.SKIPPED,
                    "Bookmark already exists in collection"
                );

                eventEmitter.accept(ProgressEvent.videoSkipped(
                    String.format("Skipped '%s' - already exists in collection", video.title()),
                    result
                ));

                return result;
            }

            // Generate tags using AI
            List<TagWithConfidence> tags = videoTaggingService.generateTags(video, existingTags, tagStrategy);
            List<String> tagNames = tags.stream()
                .map(TagWithConfidence::tag)
                .toList();

            // Create bookmark in Raindrop
            raindropService.createBookmark(collectionId, video.url(), video.title(), tagNames);

            VideoProcessingResult result = new VideoProcessingResult(
                video,
                tags,
                ProcessingStatus.SUCCESS,
                null
            );

            eventEmitter.accept(ProgressEvent.videoCompleted(
                String.format("Tagged '%s' with %d tags", video.title(), tags.size()),
                result
            ));

            log.info("Successfully processed video: {} with {} tags", video.videoId(), tags.size());
            return result;

        } catch (Exception e) {
            log.error("Failed to process video {}: {}", video.videoId(), e.getMessage(), e);

            VideoProcessingResult result = new VideoProcessingResult(
                video,
                Collections.emptyList(),
                ProcessingStatus.FAILED,
                e.getMessage()
            );

            eventEmitter.accept(ProgressEvent.error(
                String.format("Failed to process video '%s': %s", video.title(), e.getMessage()),
                result
            ));

            return result;
        }
    }

    /**
     * Builds a processing summary from the counts.
     */
    private ProcessingSummary buildSummary(int total, int succeeded, int skipped, int failed) {
        return new ProcessingSummary(total, succeeded, skipped, failed);
    }

    /**
     * Partitions a list into sublists of the given size.
     *
     * @param list the list to partition
     * @param size the maximum size of each partition
     * @return list of partitions
     */
    private <T> List<List<T>> partition(List<T> list, int size) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    /**
     * Statistics for a processed batch.
     */
    public record BatchStats(
        int batchNumber,
        int totalBatches,
        int succeeded,
        int skipped,
        int failed
    ) {}
}
