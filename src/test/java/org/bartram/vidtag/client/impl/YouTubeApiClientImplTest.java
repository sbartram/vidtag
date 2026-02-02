package org.bartram.vidtag.client.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import java.io.IOException;
import java.util.List;
import org.bartram.vidtag.model.VideoMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class YouTubeApiClientImplTest {

    private static final String API_KEY = "test-api-key";
    private static final String PLAYLIST_ID = "PLtest123";

    @Mock
    private YouTube youtube;

    @Mock
    private YouTube.PlaylistItems playlistItems;

    @Mock
    private YouTube.PlaylistItems.List playlistItemsListRequest;

    @Mock
    private YouTube.Videos videos;

    @Mock
    private YouTube.Videos.List videosListRequest;

    private YouTubeApiClientImpl client;

    @BeforeEach
    void setUp() {
        client = new YouTubeApiClientImpl(youtube, API_KEY);
    }

    @Nested
    class GetPlaylistVideos {

        @Test
        void shouldReturnVideosSuccessfully() throws IOException {
            // Given
            PlaylistItemListResponse response = createPlaylistResponse(List.of(
                    createPlaylistItem("video1", "Video One", "Description 1", "2024-01-15T10:00:00Z"),
                    createPlaylistItem("video2", "Video Two", "Description 2", "2024-01-16T10:00:00Z")));
            response.setNextPageToken(null);

            setupPlaylistItemsMock(response);
            setupMultipleVideosDurationMock(java.util.Map.of("video1", "PT10M30S", "video2", "PT5M"));

            // When
            List<VideoMetadata> result = client.getPlaylistVideos(PLAYLIST_ID);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).videoId()).isEqualTo("video1");
            assertThat(result.get(0).title()).isEqualTo("Video One");
            assertThat(result.get(0).description()).isEqualTo("Description 1");
            assertThat(result.get(0).url()).isEqualTo("https://www.youtube.com/watch?v=video1");
            assertThat(result.get(0).duration()).isEqualTo(630); // 10*60 + 30
            assertThat(result.get(1).duration()).isEqualTo(300); // 5*60
        }

        @Test
        void shouldHandlePagination() throws IOException {
            // Given - First page
            PlaylistItemListResponse firstPage = createPlaylistResponse(
                    List.of(createPlaylistItem("video1", "Video One", "Desc 1", "2024-01-15T10:00:00Z")));
            firstPage.setNextPageToken("page2token");

            // Given - Second page
            PlaylistItemListResponse secondPage = createPlaylistResponse(
                    List.of(createPlaylistItem("video2", "Video Two", "Desc 2", "2024-01-16T10:00:00Z")));
            secondPage.setNextPageToken(null);

            when(youtube.playlistItems()).thenReturn(playlistItems);
            when(playlistItems.list(List.of("snippet", "contentDetails"))).thenReturn(playlistItemsListRequest);
            when(playlistItemsListRequest.setPlaylistId(PLAYLIST_ID)).thenReturn(playlistItemsListRequest);
            when(playlistItemsListRequest.setMaxResults(50L)).thenReturn(playlistItemsListRequest);
            when(playlistItemsListRequest.setPageToken(null)).thenReturn(playlistItemsListRequest);
            when(playlistItemsListRequest.setPageToken("page2token")).thenReturn(playlistItemsListRequest);
            when(playlistItemsListRequest.setKey(API_KEY)).thenReturn(playlistItemsListRequest);
            when(playlistItemsListRequest.execute()).thenReturn(firstPage, secondPage);

            setupMultipleVideosDurationMock(java.util.Map.of("video1", "PT1M", "video2", "PT2M"));

            // When
            List<VideoMetadata> result = client.getPlaylistVideos(PLAYLIST_ID);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(VideoMetadata::videoId).containsExactly("video1", "video2");
        }

        @Test
        void shouldHandleNullDuration() throws IOException {
            // Given
            PlaylistItemListResponse response = createPlaylistResponse(
                    List.of(createPlaylistItem("video1", "Video One", "Desc", "2024-01-15T10:00:00Z")));
            response.setNextPageToken(null);

            setupPlaylistItemsMock(response);

            // Setup video duration to return null
            VideoListResponse videoResponse = new VideoListResponse();
            videoResponse.setItems(List.of());
            when(youtube.videos()).thenReturn(videos);
            when(videos.list(List.of("contentDetails"))).thenReturn(videosListRequest);
            when(videosListRequest.setId(any())).thenReturn(videosListRequest);
            when(videosListRequest.setKey(API_KEY)).thenReturn(videosListRequest);
            when(videosListRequest.execute()).thenReturn(videoResponse);

            // When
            List<VideoMetadata> result = client.getPlaylistVideos(PLAYLIST_ID);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).duration()).isNull();
        }

        @Test
        void shouldHandleDurationApiFailure() throws IOException {
            // Given
            PlaylistItemListResponse response = createPlaylistResponse(
                    List.of(createPlaylistItem("video1", "Video One", "Desc", "2024-01-15T10:00:00Z")));
            response.setNextPageToken(null);

            setupPlaylistItemsMock(response);

            // Setup video duration API to fail
            when(youtube.videos()).thenReturn(videos);
            when(videos.list(List.of("contentDetails"))).thenReturn(videosListRequest);
            when(videosListRequest.setId(any())).thenReturn(videosListRequest);
            when(videosListRequest.setKey(API_KEY)).thenReturn(videosListRequest);
            when(videosListRequest.execute()).thenThrow(new IOException("Duration API error"));

            // When
            List<VideoMetadata> result = client.getPlaylistVideos(PLAYLIST_ID);

            // Then - Should still return video with null duration
            assertThat(result).hasSize(1);
            assertThat(result.get(0).duration()).isNull();
        }

        @Test
        void shouldThrowExceptionOnPlaylistApiFailure() throws IOException {
            // Given
            when(youtube.playlistItems()).thenReturn(playlistItems);
            when(playlistItems.list(List.of("snippet", "contentDetails"))).thenReturn(playlistItemsListRequest);
            when(playlistItemsListRequest.setPlaylistId(PLAYLIST_ID)).thenReturn(playlistItemsListRequest);
            when(playlistItemsListRequest.setMaxResults(50L)).thenReturn(playlistItemsListRequest);
            when(playlistItemsListRequest.setPageToken(null)).thenReturn(playlistItemsListRequest);
            when(playlistItemsListRequest.setKey(API_KEY)).thenReturn(playlistItemsListRequest);
            when(playlistItemsListRequest.execute()).thenThrow(new IOException("Playlist API error"));

            // When/Then
            assertThatThrownBy(() -> client.getPlaylistVideos(PLAYLIST_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to fetch YouTube playlist");
        }

        @Test
        void shouldParseVariousDurationFormats() throws IOException {
            // Given
            PlaylistItemListResponse response = createPlaylistResponse(
                    List.of(createPlaylistItem("video1", "Short", "Desc", "2024-01-15T10:00:00Z")));
            response.setNextPageToken(null);

            setupPlaylistItemsMock(response);
            setupVideosDurationMock("video1", "PT1H30M45S"); // 1 hour, 30 min, 45 sec

            // When
            List<VideoMetadata> result = client.getPlaylistVideos(PLAYLIST_ID);

            // Then
            assertThat(result.get(0).duration()).isEqualTo(5445); // 1*3600 + 30*60 + 45
        }

        @Test
        void shouldHandleInvalidDurationFormat() throws IOException {
            // Given
            PlaylistItemListResponse response = createPlaylistResponse(
                    List.of(createPlaylistItem("video1", "Video", "Desc", "2024-01-15T10:00:00Z")));
            response.setNextPageToken(null);

            setupPlaylistItemsMock(response);

            // Setup video with invalid duration
            Video video = new Video();
            VideoContentDetails contentDetails = new VideoContentDetails();
            contentDetails.setDuration("INVALID"); // Not ISO 8601
            video.setContentDetails(contentDetails);
            VideoListResponse videoResponse = new VideoListResponse();
            videoResponse.setItems(List.of(video));

            when(youtube.videos()).thenReturn(videos);
            when(videos.list(List.of("contentDetails"))).thenReturn(videosListRequest);
            when(videosListRequest.setId(any())).thenReturn(videosListRequest);
            when(videosListRequest.setKey(API_KEY)).thenReturn(videosListRequest);
            when(videosListRequest.execute()).thenReturn(videoResponse);

            // When
            List<VideoMetadata> result = client.getPlaylistVideos(PLAYLIST_ID);

            // Then
            assertThat(result.get(0).duration()).isNull();
        }

        @Test
        void shouldHandleNullDurationString() throws IOException {
            // Given
            PlaylistItemListResponse response = createPlaylistResponse(
                    List.of(createPlaylistItem("video1", "Video", "Desc", "2024-01-15T10:00:00Z")));
            response.setNextPageToken(null);

            setupPlaylistItemsMock(response);

            // Setup video with null duration
            Video video = new Video();
            VideoContentDetails contentDetails = new VideoContentDetails();
            contentDetails.setDuration(null);
            video.setContentDetails(contentDetails);
            VideoListResponse videoResponse = new VideoListResponse();
            videoResponse.setItems(List.of(video));

            when(youtube.videos()).thenReturn(videos);
            when(videos.list(List.of("contentDetails"))).thenReturn(videosListRequest);
            when(videosListRequest.setId(any())).thenReturn(videosListRequest);
            when(videosListRequest.setKey(API_KEY)).thenReturn(videosListRequest);
            when(videosListRequest.execute()).thenReturn(videoResponse);

            // When
            List<VideoMetadata> result = client.getPlaylistVideos(PLAYLIST_ID);

            // Then
            assertThat(result.get(0).duration()).isNull();
        }
    }

    private PlaylistItemListResponse createPlaylistResponse(List<PlaylistItem> items) {
        PlaylistItemListResponse response = new PlaylistItemListResponse();
        response.setItems(items);
        return response;
    }

    private PlaylistItem createPlaylistItem(String videoId, String title, String description, String publishedAt) {
        PlaylistItem item = new PlaylistItem();

        PlaylistItemSnippet snippet = new PlaylistItemSnippet();
        snippet.setTitle(title);
        snippet.setDescription(description);
        snippet.setPublishedAt(new com.google.api.client.util.DateTime(publishedAt));
        item.setSnippet(snippet);

        PlaylistItemContentDetails contentDetails = new PlaylistItemContentDetails();
        contentDetails.setVideoId(videoId);
        item.setContentDetails(contentDetails);

        return item;
    }

    private void setupPlaylistItemsMock(PlaylistItemListResponse response) throws IOException {
        when(youtube.playlistItems()).thenReturn(playlistItems);
        when(playlistItems.list(List.of("snippet", "contentDetails"))).thenReturn(playlistItemsListRequest);
        when(playlistItemsListRequest.setPlaylistId(PLAYLIST_ID)).thenReturn(playlistItemsListRequest);
        when(playlistItemsListRequest.setMaxResults(50L)).thenReturn(playlistItemsListRequest);
        when(playlistItemsListRequest.setPageToken(any())).thenReturn(playlistItemsListRequest);
        when(playlistItemsListRequest.setKey(API_KEY)).thenReturn(playlistItemsListRequest);
        when(playlistItemsListRequest.execute()).thenReturn(response);
    }

    private void setupVideosDurationMock(String videoId, String duration) throws IOException {
        Video video = new Video();
        VideoContentDetails contentDetails = new VideoContentDetails();
        contentDetails.setDuration(duration);
        video.setContentDetails(contentDetails);

        VideoListResponse videoResponse = new VideoListResponse();
        videoResponse.setItems(List.of(video));

        when(youtube.videos()).thenReturn(videos);
        when(videos.list(List.of("contentDetails"))).thenReturn(videosListRequest);
        when(videosListRequest.setId(List.of(videoId))).thenReturn(videosListRequest);
        when(videosListRequest.setKey(API_KEY)).thenReturn(videosListRequest);
        when(videosListRequest.execute()).thenReturn(videoResponse);
    }

    private void setupMultipleVideosDurationMock(java.util.Map<String, String> videoDurations) throws IOException {
        when(youtube.videos()).thenReturn(videos);
        when(videos.list(List.of("contentDetails"))).thenReturn(videosListRequest);
        when(videosListRequest.setId(any())).thenReturn(videosListRequest);
        when(videosListRequest.setKey(API_KEY)).thenReturn(videosListRequest);

        when(videosListRequest.execute()).thenAnswer(invocation -> {
            // Get the video ID from the previous setId call - need to capture it differently
            return createVideoResponse("PT5M"); // Default fallback
        });

        // Use Answer to return correct duration based on video ID
        when(videosListRequest.setId(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<String> ids = invocation.getArgument(0);
            String videoId = ids.get(0);
            String duration = videoDurations.getOrDefault(videoId, "PT0S");

            // Setup execute to return the correct response for this video
            when(videosListRequest.execute()).thenReturn(createVideoResponse(duration));
            return videosListRequest;
        });
    }

    private VideoListResponse createVideoResponse(String duration) {
        Video video = new Video();
        VideoContentDetails contentDetails = new VideoContentDetails();
        contentDetails.setDuration(duration);
        video.setContentDetails(contentDetails);

        VideoListResponse response = new VideoListResponse();
        response.setItems(List.of(video));
        return response;
    }
}
