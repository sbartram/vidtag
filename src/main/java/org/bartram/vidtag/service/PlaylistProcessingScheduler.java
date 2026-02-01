package org.bartram.vidtag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.vidtag.config.SchedulerProperties;
import org.bartram.vidtag.dto.TagPlaylistRequest;
import org.bartram.vidtag.model.TagStrategy;
import org.bartram.vidtag.model.VideoFilters;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Scheduled service that processes a YouTube playlist at fixed intervals.
 * Processes all videos from the configured playlist ID through the tagging workflow.
 */
@Slf4j
@Service
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "vidtag.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PlaylistProcessingScheduler {

    private final YouTubeService youtubeService;
    private final VideoTaggingOrchestrator orchestrator;
    private final SchedulerProperties schedulerProperties;

    /**
     * Scheduled job that processes the configured YouTube playlists.
     * Runs with fixed delay (configured in hours) to prevent overlapping executions.
     * Processes playlists sequentially. Individual playlist errors are logged but do not stop execution.
     */
    @Scheduled(
        fixedDelayString = "#{${vidtag.scheduler.fixed-delay-hours} * 60 * 60 * 1000}",
        initialDelayString = "#{10 * 1000}"  // 10 second initial delay
    )
    public void processTagPlaylist() {
        if (!schedulerProperties.isEnabled()) {
            log.debug("Scheduler is disabled, skipping execution");
            return;
        }

        String playlistIdsConfig = schedulerProperties.getPlaylistIds();

        if (playlistIdsConfig == null || playlistIdsConfig.isBlank()) {
            log.error("Scheduler configured but no playlist IDs provided. Set vidtag.scheduler.playlist-ids");
            return;
        }

        // Parse comma-separated playlist IDs
        List<String> playlistIds = Arrays.stream(playlistIdsConfig.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toList();

        if (playlistIds.isEmpty()) {
            log.error("Scheduler configured but no valid playlist IDs found after parsing: '{}'", playlistIdsConfig);
            return;
        }

        log.info("Starting scheduled processing for {} playlist(s)", playlistIds.size());

        int totalCount = playlistIds.size();
        int successCount = 0;
        int failureCount = 0;

        // Process each playlist sequentially
        for (int i = 0; i < playlistIds.size(); i++) {
            String playlistId = playlistIds.get(i);
            int playlistNumber = i + 1;

            log.info("Processing playlist {} of {}: {}", playlistNumber, totalCount, playlistId);

            try {
                // Create request with default settings
                // Collection is automatically determined by AI
                TagPlaylistRequest request = new TagPlaylistRequest(
                    playlistId,
                    new VideoFilters(null, null, null),
                    TagStrategy.SUGGEST,
                    null
                );

                // Process playlist asynchronously (orchestrator handles SSE events)
                orchestrator.processPlaylist(request, event -> {
                    // Log progress events (no SSE client for scheduled job)
                    log.debug("Progress: {}", event.message());
                });

                log.info("Successfully initiated processing for playlist: {}", playlistId);
                successCount++;

            } catch (Exception e) {
                log.error("Failed to process playlist '{}': {}", playlistId, e.getMessage(), e);
                failureCount++;
                // Don't rethrow - continue to next playlist
            }
        }

        log.info("Completed scheduled processing: {} total, {} succeeded, {} failed",
            totalCount, successCount, failureCount);
    }
}
