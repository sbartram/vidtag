package org.bartram.vidtag.service;

import org.bartram.vidtag.config.RaindropProperties;
import org.bartram.vidtag.model.VideoMetadata;
import org.bartram.vidtag.service.YouTubeService.PlaylistMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollectionSelectionServiceTest {

    @Mock
    private RaindropService raindropService;

    @Mock
    private YouTubeService youtubeService;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private CallResponseSpec callResponseSpec;

    private RaindropProperties raindropProperties;
    private CacheManager cacheManager;
    private CollectionSelectionService collectionSelectionService;

    @BeforeEach
    void setUp() {
        raindropProperties = new RaindropProperties();
        raindropProperties.setFallbackCollection("Videos");
        raindropProperties.setCollectionCacheTtl(Duration.ofHours(24));
        raindropProperties.setCollectionsListCacheTtl(Duration.ofHours(1));

        cacheManager = new ConcurrentMapCacheManager("playlist-collections");

        collectionSelectionService = new CollectionSelectionService(
            raindropService,
            youtubeService,
            chatClient,
            raindropProperties,
            cacheManager
        );
    }

    @Test
    void selectCollection_cacheHit_returnsCachedValue() {
        // Arrange
        String playlistId = "PLxxx";
        String cachedCollection = "Technology";
        cacheManager.getCache("playlist-collections").put(playlistId, cachedCollection);

        // Act
        String result = collectionSelectionService.selectCollection(playlistId);

        // Assert
        assertThat(result).isEqualTo(cachedCollection);
        verifyNoInteractions(raindropService, youtubeService, chatClient);
    }

    @Test
    void selectCollection_cacheMiss_callsAI() {
        // Arrange
        String playlistId = "PLxxx";
        List<String> collections = List.of("Technology", "Cooking");
        String playlistTitle = "Java Programming";
        String playlistDescription = "Learn Java";
        List<VideoMetadata> videos = List.of(
            new VideoMetadata("v1", "https://youtube.com/v1", "Spring Boot Tutorial", "", Instant.now(), 300)
        );

        when(raindropService.getUserCollections()).thenReturn(collections);
        when(youtubeService.getPlaylistMetadata(playlistId))
            .thenReturn(new PlaylistMetadata(playlistTitle, playlistDescription));
        when(youtubeService.getPlaylistVideos(playlistId, 10)).thenReturn(videos);
        when(chatClient.prompt(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Technology");

        // Act
        String result = collectionSelectionService.selectCollection(playlistId);

        // Assert
        assertThat(result).isEqualTo("Technology");
        verify(chatClient).prompt(anyString());
    }

    @Test
    void selectCollection_aiReturnsLowConfidence_usesFallback() {
        // Arrange
        String playlistId = "PLxxx";
        setupMocksForAICall();
        when(callResponseSpec.content()).thenReturn("LOW_CONFIDENCE");

        // Act
        String result = collectionSelectionService.selectCollection(playlistId);

        // Assert
        assertThat(result).isEqualTo("Videos"); // fallback
    }

    @Test
    void selectCollection_aiReturnsInvalidCollection_usesFallback() {
        // Arrange
        String playlistId = "PLxxx";
        setupMocksForAICall();
        when(callResponseSpec.content()).thenReturn("NonExistent");

        // Act
        String result = collectionSelectionService.selectCollection(playlistId);

        // Assert
        assertThat(result).isEqualTo("Videos"); // fallback
    }

    @Test
    void selectCollection_fallbackDoesNotExist_createsIt() {
        // Arrange
        String playlistId = "PLxxx";
        setupMocksForAICall();
        when(raindropService.getUserCollections()).thenReturn(List.of("Technology"));
        when(callResponseSpec.content()).thenReturn("LOW_CONFIDENCE");
        when(raindropService.createCollection("Videos")).thenReturn(1L);

        // Act
        String result = collectionSelectionService.selectCollection(playlistId);

        // Assert
        assertThat(result).isEqualTo("Videos");
        verify(raindropService).createCollection("Videos");
    }

    private void setupMocksForAICall() {
        when(raindropService.getUserCollections()).thenReturn(List.of("Technology", "Cooking"));
        when(youtubeService.getPlaylistMetadata(anyString()))
            .thenReturn(new PlaylistMetadata("Test", "Test"));
        when(youtubeService.getPlaylistVideos(anyString(), anyInt()))
            .thenReturn(List.of(new VideoMetadata("v1", "https://youtube.com/v1", "Title", "", Instant.now(), 300)));
        when(chatClient.prompt(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
    }
}
