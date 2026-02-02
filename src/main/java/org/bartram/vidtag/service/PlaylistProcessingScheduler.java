package org.bartram.vidtag.service;

import java.util.Arrays;
import java.util.List;
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
            fixedDelayString = "#{@schedulerProperties.fixedDelay.toMillis()}",
            initialDelayString = "#{@schedulerProperties.initialDelay.toMillis()}")
    public void processTagPlaylist() {
        if (!schedulerProperties.isEnabled()) {
            log.atDebug()
                    .setMessage("Scheduler is disabled, skipping execution")
                    .log();
            return;
        }

        String playlistIdsConfig = schedulerProperties.getPlaylistIds();

        if (playlistIdsConfig == null || playlistIdsConfig.isBlank()) {
            log.atError()
                    .setMessage("Scheduler configured but no playlist IDs provided. Set vidtag.scheduler.playlist-ids")
                    .log();
            return;
        }

        // Parse comma-separated playlist IDs
        List<String> playlistIds = Arrays.stream(playlistIdsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        if (playlistIds.isEmpty()) {
            log.atError()
                    .setMessage("Scheduler configured but no valid playlist IDs found after parsing: '{}'")
                    .addArgument(playlistIdsConfig)
                    .log();
            return;
        }

        log.atInfo()
                .setMessage("Starting scheduled processing for {} playlist(s)")
                .addArgument(playlistIds.size())
                .log();

        int totalCount = playlistIds.size();
        int successCount = 0;
        int failureCount = 0;

        // Process each playlist sequentially
        for (int i = 0; i < playlistIds.size(); i++) {
            String playlistId = playlistIds.get(i);
            int playlistNumber = i + 1;

            log.atInfo()
                    .setMessage("Processing playlist {} of {}: {}")
                    .addArgument(playlistNumber)
                    .addArgument(totalCount)
                    .addArgument(playlistId)
                    .log();

            try {
                // Create request with default settings
                // Collection is automatically determined by AI
                TagPlaylistRequest request = new TagPlaylistRequest(
                        playlistId, new VideoFilters(null, null, null), TagStrategy.SUGGEST, null);

                // Process playlist asynchronously (orchestrator handles SSE events)
                orchestrator.processPlaylist(request, event -> {
                    // Log progress events (no SSE client for scheduled job)
                    log.atDebug()
                            .setMessage("Progress: {}")
                            .addArgument(event.message())
                            .log();
                });

                log.atInfo()
                        .setMessage("Successfully initiated processing for playlist: {}")
                        .addArgument(playlistId)
                        .log();
                successCount++;

            } catch (Exception e) {
                log.atError()
                        .setMessage("Failed to process playlist '{}': {}")
                        .addArgument(playlistId)
                        .addArgument(e.getMessage())
                        .setCause(e)
                        .log();
                failureCount++;
                // Don't rethrow - continue to next playlist
            }
        }

        log.atInfo()
                .setMessage("Completed scheduled processing: {} total, {} succeeded, {} failed")
                .addArgument(totalCount)
                .addArgument(successCount)
                .addArgument(failureCount)
                .log();
    }
}
