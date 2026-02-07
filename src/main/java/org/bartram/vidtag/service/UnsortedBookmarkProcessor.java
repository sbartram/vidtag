package org.bartram.vidtag.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.vidtag.config.UnsortedProcessorProperties;
import org.bartram.vidtag.model.Raindrop;
import org.bartram.vidtag.model.RaindropTag;
import org.bartram.vidtag.model.TagStrategy;
import org.bartram.vidtag.model.TagWithConfidence;
import org.bartram.vidtag.model.VideoMetadata;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled service that processes unsorted Raindrop bookmarks.
 * Picks up YouTube bookmarks from the Unsorted collection, generates AI tags,
 * selects the appropriate collection, and moves them.
 */
@Slf4j
@Service
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "vidtag.unsorted-processor", name = "enabled", havingValue = "true")
public class UnsortedBookmarkProcessor {

    private static final String DEFAULT_USER_ID = "default";

    private final RaindropService raindropService;
    private final YouTubeService youtubeService;
    private final VideoTaggingService videoTaggingService;
    private final CollectionSelectionService collectionSelectionService;
    private final UnsortedProcessorProperties properties;

    /**
     * Scheduled job that processes unsorted YouTube bookmarks.
     * Fetches unsorted raindrops, filters for YouTube URLs, generates tags,
     * selects collections, and moves bookmarks.
     */
    @Scheduled(
            fixedDelayString = "#{@unsortedProcessorProperties.fixedDelay.toMillis()}",
            initialDelayString = "#{@unsortedProcessorProperties.initialDelay.toMillis()}")
    public void processUnsortedBookmarks() {
        if (!properties.isEnabled()) {
            log.atDebug()
                    .setMessage("Unsorted bookmark processor is disabled, skipping execution")
                    .log();
            return;
        }

        log.atInfo().setMessage("Starting unsorted bookmark processing").log();

        List<Raindrop> unsortedRaindrops;
        try {
            unsortedRaindrops = raindropService.getUnsortedRaindrops();
        } catch (Exception e) {
            log.atError()
                    .setMessage("Failed to fetch unsorted raindrops: {}")
                    .addArgument(e.getMessage())
                    .setCause(e)
                    .log();
            return;
        }

        if (unsortedRaindrops.isEmpty()) {
            log.atInfo().setMessage("No unsorted bookmarks found").log();
            return;
        }

        // Filter for YouTube URLs only
        List<Raindrop> youtubeRaindrops = unsortedRaindrops.stream()
                .filter(r -> youtubeService.extractVideoId(r.link()) != null)
                .toList();

        log.atInfo()
                .setMessage("Found {} unsorted bookmarks, {} are YouTube videos")
                .addArgument(unsortedRaindrops.size())
                .addArgument(youtubeRaindrops.size())
                .log();

        if (youtubeRaindrops.isEmpty()) {
            log.atInfo()
                    .setMessage("No YouTube bookmarks in unsorted collection")
                    .log();
            return;
        }

        // Fetch existing tags once for all videos
        List<RaindropTag> existingTags;
        try {
            existingTags = raindropService.getUserTags(DEFAULT_USER_ID);
        } catch (Exception e) {
            log.atWarn()
                    .setMessage("Failed to fetch existing tags, proceeding without: {}")
                    .addArgument(e.getMessage())
                    .log();
            existingTags = List.of();
        }

        int succeeded = 0;
        int failed = 0;
        int skipped = 0;

        for (Raindrop raindrop : youtubeRaindrops) {
            try {
                boolean processed = processRaindrop(raindrop, existingTags);
                if (processed) {
                    succeeded++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.atError()
                        .setMessage("Failed to process raindrop '{}': {}")
                        .addArgument(raindrop.title())
                        .addArgument(e.getMessage())
                        .setCause(e)
                        .log();
                failed++;
            }
        }

        log.atInfo()
                .setMessage(
                        "Unsorted bookmark processing completed: {} succeeded, {} skipped, {} failed out of {} YouTube bookmarks")
                .addArgument(succeeded)
                .addArgument(skipped)
                .addArgument(failed)
                .addArgument(youtubeRaindrops.size())
                .log();
    }

    private boolean processRaindrop(Raindrop raindrop, List<RaindropTag> existingTags) {
        String videoId = youtubeService.extractVideoId(raindrop.link());
        if (videoId == null) {
            log.atDebug()
                    .setMessage("Skipping non-YouTube raindrop: {}")
                    .addArgument(raindrop.link())
                    .log();
            return false;
        }

        log.atDebug()
                .setMessage("Processing unsorted bookmark: {} (videoId={})")
                .addArgument(raindrop.title())
                .addArgument(videoId)
                .log();

        // Fetch video metadata from YouTube
        VideoMetadata video = youtubeService.getVideoMetadata(videoId);
        if (video == null) {
            log.atWarn()
                    .setMessage("Video not found on YouTube, skipping: {}")
                    .addArgument(videoId)
                    .log();
            return false;
        }

        // Generate tags using AI
        List<TagWithConfidence> tags = videoTaggingService.generateTags(video, existingTags, TagStrategy.SUGGEST);
        List<String> tagNames = tags.stream().map(TagWithConfidence::tag).toList();

        // Select collection using AI
        String collectionTitle = collectionSelectionService.selectCollectionForVideo(video);
        Long collectionId = raindropService.resolveCollectionId(DEFAULT_USER_ID, collectionTitle);

        // Update the raindrop: move to collection and add tags
        raindropService.updateRaindrop(raindrop.id(), collectionId, tagNames);

        log.atInfo()
                .setMessage("Processed unsorted bookmark '{}': moved to '{}' with {} tags")
                .addArgument(raindrop.title())
                .addArgument(collectionTitle)
                .addArgument(tagNames.size())
                .log();

        return true;
    }
}
