package org.bartram.vidtag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.bartram.vidtag.dto.TagPlaylistRequest;
import org.bartram.vidtag.event.ProgressEvent;
import org.bartram.vidtag.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for VideoTaggingOrchestrator.
 */
@ExtendWith(MockitoExtension.class)
class VideoTaggingOrchestratorTest {

    @Mock
    private YouTubeService youtubeService;

    @Mock
    private RaindropService raindropService;

    @Mock
    private VideoTaggingService videoTaggingService;

    @Mock
    private CollectionSelectionService collectionSelectionService;

    private VideoTaggingOrchestrator orchestrator;

    private List<ProgressEvent> capturedEvents;
    private Consumer<ProgressEvent> eventCaptor;

    @BeforeEach
    void setUp() {
        orchestrator = new VideoTaggingOrchestrator(
                youtubeService, raindropService, videoTaggingService, collectionSelectionService);
        // Default mock behavior for collection selection
        when(collectionSelectionService.selectCollection(anyString())).thenReturn("Tech Videos");
        capturedEvents = new ArrayList<>();
        eventCaptor = capturedEvents::add;
    }

    @Test
    void processPlaylist_shouldEmitStartedEvent() {
        // Given
        TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, null, null);

        when(youtubeService.extractPlaylistId("PLtest123")).thenReturn("PLtest123");
        when(raindropService.resolveCollectionId(eq("default"), eq("Tech Videos")))
                .thenReturn(1001L);
        when(raindropService.getUserTags("default")).thenReturn(List.of());
        when(youtubeService.fetchPlaylistVideos(eq("PLtest123"), isNull())).thenReturn(List.of());

        // When
        orchestrator.processPlaylist(request, eventCaptor);

        // Then
        assertThat(capturedEvents).isNotEmpty();
        assertThat(capturedEvents.get(0).eventType()).isEqualTo("started");
        assertThat(capturedEvents.get(0).message()).contains("Processing playlist");
    }

    @Test
    void processPlaylist_shouldEmitCompletedEventWithSummary() {
        // Given
        TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, null, null);

        when(youtubeService.extractPlaylistId("PLtest123")).thenReturn("PLtest123");
        when(raindropService.resolveCollectionId(eq("default"), eq("Tech Videos")))
                .thenReturn(1001L);
        when(raindropService.getUserTags("default")).thenReturn(List.of());
        when(youtubeService.fetchPlaylistVideos(eq("PLtest123"), isNull())).thenReturn(List.of());

        // When
        orchestrator.processPlaylist(request, eventCaptor);

        // Then
        ProgressEvent lastEvent = capturedEvents.get(capturedEvents.size() - 1);
        assertThat(lastEvent.eventType()).isEqualTo("completed");
        assertThat(lastEvent.data()).isInstanceOf(ProcessingSummary.class);
    }

    @Test
    void processPlaylist_shouldSkipDuplicateVideos() {
        // Given
        TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, null, null);

        VideoMetadata video = new VideoMetadata(
                "video1", "https://youtube.com/watch?v=video1", "Java Tutorial", "Description", Instant.now(), 600);

        when(youtubeService.extractPlaylistId("PLtest123")).thenReturn("PLtest123");
        when(raindropService.resolveCollectionId(eq("default"), eq("Tech Videos")))
                .thenReturn(1001L);
        when(raindropService.getUserTags("default")).thenReturn(List.of());
        when(youtubeService.fetchPlaylistVideos(eq("PLtest123"), isNull())).thenReturn(List.of(video));
        when(raindropService.bookmarkExists(eq(1001L), eq("https://youtube.com/watch?v=video1")))
                .thenReturn(true);

        // When
        orchestrator.processPlaylist(request, eventCaptor);

        // Then
        List<ProgressEvent> skippedEvents = capturedEvents.stream()
                .filter(e -> "videoSkipped".equals(e.eventType()))
                .toList();
        assertThat(skippedEvents).hasSize(1);
        assertThat(skippedEvents.get(0).message()).contains("already exists");

        verify(videoTaggingService, never()).generateTags(any(), any(), any());
    }

    @Test
    void processPlaylist_shouldProcessAndTagVideos() {
        // Given
        TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, new TagStrategy(5, 0.5, null), null);

        VideoMetadata video = new VideoMetadata(
                "video1", "https://youtube.com/watch?v=video1", "Java Tutorial", "Description", Instant.now(), 600);

        List<RaindropTag> existingTags = List.of(new RaindropTag("java"));
        List<TagWithConfidence> generatedTags =
                List.of(new TagWithConfidence("java", 0.95, true), new TagWithConfidence("tutorial", 0.85, false));

        when(youtubeService.extractPlaylistId("PLtest123")).thenReturn("PLtest123");
        when(raindropService.resolveCollectionId(eq("default"), eq("Tech Videos")))
                .thenReturn(1001L);
        when(raindropService.getUserTags("default")).thenReturn(existingTags);
        when(youtubeService.fetchPlaylistVideos(eq("PLtest123"), isNull())).thenReturn(List.of(video));
        when(raindropService.bookmarkExists(eq(1001L), anyString())).thenReturn(false);
        when(videoTaggingService.generateTags(eq(video), eq(existingTags), any()))
                .thenReturn(generatedTags);

        // When
        orchestrator.processPlaylist(request, eventCaptor);

        // Then
        List<ProgressEvent> completedEvents = capturedEvents.stream()
                .filter(e -> "videoCompleted".equals(e.eventType()))
                .toList();
        assertThat(completedEvents).hasSize(1);

        verify(raindropService)
                .createBookmark(
                        eq(1001L),
                        eq("https://youtube.com/watch?v=video1"),
                        eq("Java Tutorial"),
                        eq(List.of("java", "tutorial")));
    }

    @Test
    void processPlaylist_shouldEmitBatchCompletedAfterBatch() {
        // Given
        TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, null, null);

        // Create 12 videos to trigger 2 batches (10 + 2)
        List<VideoMetadata> videos = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            videos.add(new VideoMetadata(
                    "video" + i,
                    "https://youtube.com/watch?v=video" + i,
                    "Video " + i,
                    "Description " + i,
                    Instant.now(),
                    600));
        }

        when(youtubeService.extractPlaylistId("PLtest123")).thenReturn("PLtest123");
        when(raindropService.resolveCollectionId(eq("default"), eq("Tech Videos")))
                .thenReturn(1001L);
        when(raindropService.getUserTags("default")).thenReturn(List.of());
        when(youtubeService.fetchPlaylistVideos(eq("PLtest123"), isNull())).thenReturn(videos);
        when(raindropService.bookmarkExists(eq(1001L), anyString())).thenReturn(false);
        when(videoTaggingService.generateTags(any(), any(), any()))
                .thenReturn(List.of(new TagWithConfidence("test", 0.9, false)));

        // When
        orchestrator.processPlaylist(request, eventCaptor);

        // Then
        List<ProgressEvent> batchEvents = capturedEvents.stream()
                .filter(e -> "batchCompleted".equals(e.eventType()))
                .toList();
        assertThat(batchEvents).hasSize(2); // 2 batches
    }

    @Test
    void processPlaylist_shouldHandleCollectionNotFound() {
        // Given
        TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, null, null);

        when(youtubeService.extractPlaylistId("PLtest123")).thenReturn("PLtest123");
        when(collectionSelectionService.selectCollection("PLtest123")).thenReturn("Nonexistent Collection");
        when(raindropService.resolveCollectionId(eq("default"), eq("Nonexistent Collection")))
                .thenReturn(null);

        // When
        orchestrator.processPlaylist(request, eventCaptor);

        // Then
        List<ProgressEvent> errorEvents = capturedEvents.stream()
                .filter(e -> "error".equals(e.eventType()))
                .toList();
        assertThat(errorEvents).isNotEmpty();
        assertThat(errorEvents.get(0).message()).contains("Collection not found");

        ProgressEvent lastEvent = capturedEvents.get(capturedEvents.size() - 1);
        assertThat(lastEvent.eventType()).isEqualTo("completed");
    }

    @Test
    void processPlaylist_shouldHandleVideoProcessingErrors() {
        // Given
        TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, null, null);

        VideoMetadata video = new VideoMetadata(
                "video1", "https://youtube.com/watch?v=video1", "Java Tutorial", "Description", Instant.now(), 600);

        when(youtubeService.extractPlaylistId("PLtest123")).thenReturn("PLtest123");
        when(raindropService.resolveCollectionId(eq("default"), eq("Tech Videos")))
                .thenReturn(1001L);
        when(raindropService.getUserTags("default")).thenReturn(List.of());
        when(youtubeService.fetchPlaylistVideos(eq("PLtest123"), isNull())).thenReturn(List.of(video));
        when(raindropService.bookmarkExists(eq(1001L), anyString())).thenReturn(false);
        when(videoTaggingService.generateTags(any(), any(), any()))
                .thenThrow(new RuntimeException("AI service unavailable"));

        // When
        orchestrator.processPlaylist(request, eventCaptor);

        // Then
        List<ProgressEvent> errorEvents = capturedEvents.stream()
                .filter(e -> "error".equals(e.eventType()))
                .toList();
        assertThat(errorEvents).hasSize(1);
        assertThat(errorEvents.get(0).message()).contains("Failed to process video");

        // Processing should continue and complete
        ProgressEvent lastEvent = capturedEvents.get(capturedEvents.size() - 1);
        assertThat(lastEvent.eventType()).isEqualTo("completed");

        ProcessingSummary summary = (ProcessingSummary) lastEvent.data();
        assertThat(summary.failed()).isEqualTo(1);
    }

    @Test
    void processPlaylist_shouldApplyVideoFilters() {
        // Given
        VideoFilters filters = new VideoFilters(Instant.now().minusSeconds(86400), 1800, 5);
        TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", filters, null, null);

        when(youtubeService.extractPlaylistId("PLtest123")).thenReturn("PLtest123");
        when(raindropService.resolveCollectionId(eq("default"), eq("Tech Videos")))
                .thenReturn(1001L);
        when(raindropService.getUserTags("default")).thenReturn(List.of());
        when(youtubeService.fetchPlaylistVideos(eq("PLtest123"), eq(filters))).thenReturn(List.of());

        // When
        orchestrator.processPlaylist(request, eventCaptor);

        // Then
        verify(youtubeService).fetchPlaylistVideos("PLtest123", filters);
    }

    @Test
    void processPlaylist_shouldExtractPlaylistIdFromUrl() {
        // Given
        String playlistUrl = "https://www.youtube.com/playlist?list=PLtest123";
        TagPlaylistRequest request = new TagPlaylistRequest(playlistUrl, null, null, null);

        when(youtubeService.extractPlaylistId(playlistUrl)).thenReturn("PLtest123");
        when(raindropService.resolveCollectionId(eq("default"), eq("Tech Videos")))
                .thenReturn(1001L);
        when(raindropService.getUserTags("default")).thenReturn(List.of());
        when(youtubeService.fetchPlaylistVideos(eq("PLtest123"), isNull())).thenReturn(List.of());

        // When
        orchestrator.processPlaylist(request, eventCaptor);

        // Then
        verify(youtubeService).extractPlaylistId(playlistUrl);
        verify(youtubeService).fetchPlaylistVideos("PLtest123", null);
    }

    @Test
    void processPlaylist_shouldBuildCorrectSummary() {
        // Given
        TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, null, null);

        List<VideoMetadata> videos = List.of(
                new VideoMetadata("v1", "https://youtube.com/watch?v=v1", "Video 1", null, Instant.now(), 600),
                new VideoMetadata("v2", "https://youtube.com/watch?v=v2", "Video 2", null, Instant.now(), 600),
                new VideoMetadata("v3", "https://youtube.com/watch?v=v3", "Video 3", null, Instant.now(), 600));

        when(youtubeService.extractPlaylistId("PLtest123")).thenReturn("PLtest123");
        when(raindropService.resolveCollectionId(eq("default"), eq("Tech Videos")))
                .thenReturn(1001L);
        when(raindropService.getUserTags("default")).thenReturn(List.of());
        when(youtubeService.fetchPlaylistVideos(eq("PLtest123"), isNull())).thenReturn(videos);

        // v1 - already exists (skipped)
        when(raindropService.bookmarkExists(eq(1001L), eq("https://youtube.com/watch?v=v1")))
                .thenReturn(true);
        // v2 - successful
        when(raindropService.bookmarkExists(eq(1001L), eq("https://youtube.com/watch?v=v2")))
                .thenReturn(false);
        when(videoTaggingService.generateTags(eq(videos.get(1)), any(), any()))
                .thenReturn(List.of(new TagWithConfidence("test", 0.9, false)));
        // v3 - fails
        when(raindropService.bookmarkExists(eq(1001L), eq("https://youtube.com/watch?v=v3")))
                .thenReturn(false);
        when(videoTaggingService.generateTags(eq(videos.get(2)), any(), any()))
                .thenThrow(new RuntimeException("AI error"));

        // When
        orchestrator.processPlaylist(request, eventCaptor);

        // Then
        ProgressEvent lastEvent = capturedEvents.get(capturedEvents.size() - 1);
        assertThat(lastEvent.eventType()).isEqualTo("completed");

        ProcessingSummary summary = (ProcessingSummary) lastEvent.data();
        assertThat(summary.totalVideos()).isEqualTo(3);
        assertThat(summary.succeeded()).isEqualTo(1);
        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(1);
    }

    @Test
    void processPlaylist_shouldEmitProgressEventForFetchingVideos() {
        // Given
        TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, null, null);

        when(youtubeService.extractPlaylistId("PLtest123")).thenReturn("PLtest123");
        when(raindropService.resolveCollectionId(eq("default"), eq("Tech Videos")))
                .thenReturn(1001L);
        when(raindropService.getUserTags("default")).thenReturn(List.of());
        when(youtubeService.fetchPlaylistVideos(eq("PLtest123"), isNull())).thenReturn(List.of());

        // When
        orchestrator.processPlaylist(request, eventCaptor);

        // Then
        List<ProgressEvent> progressEvents = capturedEvents.stream()
                .filter(e -> "progress".equals(e.eventType()))
                .toList();
        assertThat(progressEvents).isNotEmpty();
        assertThat(progressEvents.stream().anyMatch(e -> e.message().contains("Fetching")))
                .isTrue();
    }

    @Test
    void processPlaylist_shouldContinueProcessingAfterIndividualVideoFailure() {
        // Given
        TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, null, null);

        List<VideoMetadata> videos = List.of(
                new VideoMetadata("v1", "https://youtube.com/watch?v=v1", "Video 1", null, Instant.now(), 600),
                new VideoMetadata("v2", "https://youtube.com/watch?v=v2", "Video 2", null, Instant.now(), 600));

        when(youtubeService.extractPlaylistId("PLtest123")).thenReturn("PLtest123");
        when(raindropService.resolveCollectionId(eq("default"), eq("Tech Videos")))
                .thenReturn(1001L);
        when(raindropService.getUserTags("default")).thenReturn(List.of());
        when(youtubeService.fetchPlaylistVideos(eq("PLtest123"), isNull())).thenReturn(videos);
        when(raindropService.bookmarkExists(anyLong(), anyString())).thenReturn(false);

        // First video fails
        when(videoTaggingService.generateTags(eq(videos.get(0)), any(), any()))
                .thenThrow(new RuntimeException("AI error"));
        // Second video succeeds
        when(videoTaggingService.generateTags(eq(videos.get(1)), any(), any()))
                .thenReturn(List.of(new TagWithConfidence("test", 0.9, false)));

        // When
        orchestrator.processPlaylist(request, eventCaptor);

        // Then
        // Both videos should be processed
        verify(videoTaggingService, times(2)).generateTags(any(), any(), any());

        // Second video's bookmark should be created
        verify(raindropService)
                .createBookmark(eq(1001L), eq("https://youtube.com/watch?v=v2"), eq("Video 2"), anyList());

        ProcessingSummary summary = (ProcessingSummary)
                capturedEvents.get(capturedEvents.size() - 1).data();
        assertThat(summary.succeeded()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(1);
    }

    @Test
    void processPlaylist_shouldUseTagStrategyFromRequest() {
        // Given
        TagStrategy strategy = new TagStrategy(3, 0.8, "Focus on programming");
        TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, strategy, null);

        VideoMetadata video = new VideoMetadata(
                "video1", "https://youtube.com/watch?v=video1", "Java Tutorial", "Description", Instant.now(), 600);

        when(youtubeService.extractPlaylistId("PLtest123")).thenReturn("PLtest123");
        when(raindropService.resolveCollectionId(eq("default"), eq("Tech Videos")))
                .thenReturn(1001L);
        when(raindropService.getUserTags("default")).thenReturn(List.of());
        when(youtubeService.fetchPlaylistVideos(eq("PLtest123"), isNull())).thenReturn(List.of(video));
        when(raindropService.bookmarkExists(anyLong(), anyString())).thenReturn(false);
        when(videoTaggingService.generateTags(any(), any(), eq(strategy)))
                .thenReturn(List.of(new TagWithConfidence("java", 0.9, false)));

        // When
        orchestrator.processPlaylist(request, eventCaptor);

        // Then
        verify(videoTaggingService).generateTags(eq(video), any(), eq(strategy));
    }
}
