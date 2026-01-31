package org.bartram.vidtag.service;

import org.bartram.vidtag.client.YouTubeApiClient;
import org.bartram.vidtag.model.VideoFilters;
import org.bartram.vidtag.model.VideoMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for YouTubeService.
 */
@ExtendWith(MockitoExtension.class)
class YouTubeServiceTest {

    @Mock
    private YouTubeApiClient youtubeApiClient;

    @InjectMocks
    private YouTubeService youtubeService;

    @Test
    void extractPlaylistId_withFullUrl_shouldExtractId() {
        // Given
        String playlistUrl = "https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf";

        // When
        String playlistId = youtubeService.extractPlaylistId(playlistUrl);

        // Then
        assertEquals("PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf", playlistId);
    }

    @Test
    void extractPlaylistId_withUrlContainingOtherParams_shouldExtractId() {
        // Given
        String playlistUrl = "https://www.youtube.com/watch?v=someVideo&list=PLtest123&index=1";

        // When
        String playlistId = youtubeService.extractPlaylistId(playlistUrl);

        // Then
        assertEquals("PLtest123", playlistId);
    }

    @Test
    void extractPlaylistId_withJustId_shouldReturnId() {
        // Given
        String playlistId = "PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf";

        // When
        String result = youtubeService.extractPlaylistId(playlistId);

        // Then
        assertEquals(playlistId, result);
    }

    @Test
    void extractPlaylistId_withShortUrl_shouldExtractId() {
        // Given
        String playlistUrl = "youtube.com/playlist?list=PLabc-123_XYZ";

        // When
        String playlistId = youtubeService.extractPlaylistId(playlistUrl);

        // Then
        assertEquals("PLabc-123_XYZ", playlistId);
    }

    @Test
    void fetchPlaylistVideos_withNoFilters_shouldReturnAllVideos() {
        // Given
        String playlistId = "PLtest123";
        List<VideoMetadata> mockVideos = List.of(
            new VideoMetadata("video1", "https://youtube.com/watch?v=video1", "Video 1", "Description 1", Instant.now(), 300),
            new VideoMetadata("video2", "https://youtube.com/watch?v=video2", "Video 2", "Description 2", Instant.now(), 600)
        );
        when(youtubeApiClient.getPlaylistVideos(playlistId)).thenReturn(mockVideos);

        // When
        List<VideoMetadata> result = youtubeService.fetchPlaylistVideos(playlistId, null);

        // Then
        assertEquals(2, result.size());
        verify(youtubeApiClient, times(1)).getPlaylistVideos(playlistId);
    }

    @Test
    void fetchPlaylistVideos_withPublishedAfterFilter_shouldFilterOldVideos() {
        // Given
        String playlistId = "PLtest123";
        Instant cutoffDate = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant recentDate = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant oldDate = Instant.now().minus(60, ChronoUnit.DAYS);

        List<VideoMetadata> mockVideos = List.of(
            new VideoMetadata("video1", "https://youtube.com/watch?v=video1", "Recent Video", "Description", recentDate, 300),
            new VideoMetadata("video2", "https://youtube.com/watch?v=video2", "Old Video", "Description", oldDate, 600)
        );
        when(youtubeApiClient.getPlaylistVideos(playlistId)).thenReturn(mockVideos);

        VideoFilters filters = new VideoFilters(cutoffDate, null, null);

        // When
        List<VideoMetadata> result = youtubeService.fetchPlaylistVideos(playlistId, filters);

        // Then
        assertEquals(1, result.size());
        assertEquals("video1", result.get(0).videoId());
        assertTrue(result.get(0).publishedAt().isAfter(cutoffDate));
    }

    @Test
    void fetchPlaylistVideos_withMaxDurationFilter_shouldFilterLongVideos() {
        // Given
        String playlistId = "PLtest123";
        List<VideoMetadata> mockVideos = List.of(
            new VideoMetadata("video1", "https://youtube.com/watch?v=video1", "Short Video", "Description", Instant.now(), 300),
            new VideoMetadata("video2", "https://youtube.com/watch?v=video2", "Long Video", "Description", Instant.now(), 1800)
        );
        when(youtubeApiClient.getPlaylistVideos(playlistId)).thenReturn(mockVideos);

        VideoFilters filters = new VideoFilters(null, 600, null);

        // When
        List<VideoMetadata> result = youtubeService.fetchPlaylistVideos(playlistId, filters);

        // Then
        assertEquals(1, result.size());
        assertEquals("video1", result.get(0).videoId());
        assertTrue(result.get(0).duration() <= 600);
    }

    @Test
    void fetchPlaylistVideos_withMaxVideosFilter_shouldLimitResults() {
        // Given
        String playlistId = "PLtest123";
        List<VideoMetadata> mockVideos = List.of(
            new VideoMetadata("video1", "https://youtube.com/watch?v=video1", "Video 1", "Description", Instant.now(), 300),
            new VideoMetadata("video2", "https://youtube.com/watch?v=video2", "Video 2", "Description", Instant.now(), 400),
            new VideoMetadata("video3", "https://youtube.com/watch?v=video3", "Video 3", "Description", Instant.now(), 500)
        );
        when(youtubeApiClient.getPlaylistVideos(playlistId)).thenReturn(mockVideos);

        VideoFilters filters = new VideoFilters(null, null, 2);

        // When
        List<VideoMetadata> result = youtubeService.fetchPlaylistVideos(playlistId, filters);

        // Then
        assertEquals(2, result.size());
    }

    @Test
    void fetchPlaylistVideos_withAllFilters_shouldApplyAllFilters() {
        // Given
        String playlistId = "PLtest123";
        Instant cutoffDate = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant recentDate1 = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant recentDate2 = Instant.now().minus(15, ChronoUnit.DAYS);
        Instant oldDate = Instant.now().minus(60, ChronoUnit.DAYS);

        List<VideoMetadata> mockVideos = List.of(
            new VideoMetadata("video1", "https://youtube.com/watch?v=video1", "Recent Short 1", "Description", recentDate1, 300),
            new VideoMetadata("video2", "https://youtube.com/watch?v=video2", "Recent Short 2", "Description", recentDate2, 400),
            new VideoMetadata("video3", "https://youtube.com/watch?v=video3", "Recent Long", "Description", recentDate1, 1800),
            new VideoMetadata("video4", "https://youtube.com/watch?v=video4", "Old Short", "Description", oldDate, 300)
        );
        when(youtubeApiClient.getPlaylistVideos(playlistId)).thenReturn(mockVideos);

        VideoFilters filters = new VideoFilters(cutoffDate, 600, 1);

        // When
        List<VideoMetadata> result = youtubeService.fetchPlaylistVideos(playlistId, filters);

        // Then
        assertEquals(1, result.size());
        assertEquals("video1", result.get(0).videoId());
    }
}
