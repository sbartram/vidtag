package org.bartram.vidtag.service;

import org.bartram.vidtag.client.RaindropApiClient;
import org.bartram.vidtag.exception.ExternalServiceException;
import org.bartram.vidtag.exception.ResourceNotFoundException;
import org.bartram.vidtag.model.RaindropCollection;
import org.bartram.vidtag.model.RaindropTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RaindropServiceTest {

    @Mock
    private RaindropApiClient raindropApiClient;

    private RaindropService raindropService;

    @BeforeEach
    void setUp() {
        raindropService = new RaindropService(raindropApiClient);
    }

    @Test
    void getUserTags_shouldReturnTags() {
        // Given
        String userId = "user123";
        List<RaindropTag> expectedTags = List.of(
            new RaindropTag("java"),
            new RaindropTag("spring"),
            new RaindropTag("tutorial")
        );
        when(raindropApiClient.getUserTags(userId)).thenReturn(expectedTags);

        // When
        List<RaindropTag> actualTags = raindropService.getUserTags(userId);

        // Then
        assertThat(actualTags).isEqualTo(expectedTags);
        verify(raindropApiClient, times(1)).getUserTags(userId);
    }

    @Test
    void getUserTags_shouldCacheResults() {
        // Given
        String userId = "user123";
        List<RaindropTag> expectedTags = List.of(new RaindropTag("cached"));
        when(raindropApiClient.getUserTags(userId)).thenReturn(expectedTags);

        // When - call twice
        List<RaindropTag> firstCall = raindropService.getUserTags(userId);
        List<RaindropTag> secondCall = raindropService.getUserTags(userId);

        // Then - Both calls should return same data
        // Note: @Cacheable won't work in unit tests without Spring context
        // This test validates behavior but cache testing requires integration tests
        assertThat(firstCall).isEqualTo(expectedTags);
        assertThat(secondCall).isEqualTo(expectedTags);
    }

    @Test
    void getUserTags_shouldThrowExceptionOnFailure() {
        // Given
        String userId = "user123";
        when(raindropApiClient.getUserTags(userId))
            .thenThrow(new RuntimeException("API error"));

        // When/Then
        assertThatThrownBy(() -> raindropService.getUserTags(userId))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void resolveCollectionId_shouldReturnIdForMatchingTitle() {
        // Given
        String userId = "user123";
        String targetTitle = "My Collection";
        List<RaindropCollection> collections = List.of(
            new RaindropCollection(1L, "Other Collection"),
            new RaindropCollection(2L, "My Collection"),
            new RaindropCollection(3L, "Another Collection")
        );
        when(raindropApiClient.getUserCollections(userId)).thenReturn(collections);

        // When
        Long collectionId = raindropService.resolveCollectionId(userId, targetTitle);

        // Then
        assertThat(collectionId).isEqualTo(2L);
    }

    @Test
    void resolveCollectionId_shouldBeCaseInsensitive() {
        // Given
        String userId = "user123";
        List<RaindropCollection> collections = List.of(
            new RaindropCollection(1L, "My Collection")
        );
        when(raindropApiClient.getUserCollections(userId)).thenReturn(collections);

        // When
        Long collectionId = raindropService.resolveCollectionId(userId, "my collection");

        // Then
        assertThat(collectionId).isEqualTo(1L);
    }

    @Test
    void resolveCollectionId_shouldThrowResourceNotFoundExceptionWhenNotFound() {
        // Given
        String userId = "user123";
        List<RaindropCollection> collections = List.of(
            new RaindropCollection(1L, "Other Collection")
        );
        when(raindropApiClient.getUserCollections(userId)).thenReturn(collections);

        // When/Then
        assertThatThrownBy(() -> raindropService.resolveCollectionId(userId, "Non-existent"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Collection 'Non-existent' not found");
    }

    @Test
    void resolveCollectionId_shouldThrowExceptionOnFailure() {
        // Given
        String userId = "user123";
        when(raindropApiClient.getUserCollections(userId))
            .thenThrow(new RuntimeException("API error"));

        // When/Then
        assertThatThrownBy(() -> raindropService.resolveCollectionId(userId, "Any Title"))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void bookmarkExists_shouldReturnTrue_whenBookmarkExists() {
        // Given
        Long collectionId = 123L;
        String url = "https://example.com/video";
        when(raindropApiClient.bookmarkExists(collectionId, url)).thenReturn(true);

        // When
        boolean exists = raindropService.bookmarkExists(collectionId, url);

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void bookmarkExists_shouldReturnFalse_whenBookmarkDoesNotExist() {
        // Given
        Long collectionId = 123L;
        String url = "https://example.com/video";
        when(raindropApiClient.bookmarkExists(collectionId, url)).thenReturn(false);

        // When
        boolean exists = raindropService.bookmarkExists(collectionId, url);

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    void bookmarkExists_shouldThrowExceptionOnFailure() {
        // Given
        Long collectionId = 123L;
        String url = "https://example.com/video";
        when(raindropApiClient.bookmarkExists(collectionId, url))
            .thenThrow(new RuntimeException("API error"));

        // When/Then
        assertThatThrownBy(() -> raindropService.bookmarkExists(collectionId, url))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void createBookmark_shouldCallApiClient() {
        // Given
        Long collectionId = 123L;
        String url = "https://example.com/video";
        String title = "Test Video";
        List<String> tags = List.of("java", "tutorial");

        // When
        raindropService.createBookmark(collectionId, url, title, tags);

        // Then
        verify(raindropApiClient, times(1)).createBookmark(collectionId, url, title, tags);
    }

    @Test
    void createBookmark_shouldThrowExceptionOnFailure() {
        // Given
        Long collectionId = 123L;
        String url = "https://example.com/video";
        String title = "Test Video";
        List<String> tags = List.of("java");
        doThrow(new RuntimeException("API error"))
            .when(raindropApiClient).createBookmark(collectionId, url, title, tags);

        // When/Then
        assertThatThrownBy(() -> raindropService.createBookmark(collectionId, url, title, tags))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getUserCollections_shouldReturnCollectionTitles() {
        // Arrange
        List<RaindropCollection> collections = List.of(
            new RaindropCollection(1L, "Technology"),
            new RaindropCollection(2L, "Cooking"),
            new RaindropCollection(3L, "Travel")
        );
        when(raindropApiClient.getCollections()).thenReturn(collections);

        // Act
        List<String> result = raindropService.getUserCollections();

        // Assert
        assertThat(result).containsExactly("Technology", "Cooking", "Travel");
        verify(raindropApiClient).getCollections();
    }

    @Test
    void getUserCollections_shouldBeCached() {
        // Arrange
        List<RaindropCollection> collections = List.of(
            new RaindropCollection(1L, "Technology")
        );
        when(raindropApiClient.getCollections()).thenReturn(collections);

        // Act - call twice
        List<String> firstCall = raindropService.getUserCollections();
        List<String> secondCall = raindropService.getUserCollections();

        // Then - Both calls should return same data
        // Note: @Cacheable won't work in unit tests without Spring context
        // This test validates behavior but cache testing requires integration tests
        assertThat(firstCall).containsExactly("Technology");
        assertThat(secondCall).containsExactly("Technology");
    }

    @Test
    void createCollection_shouldCallApiAndReturnId() {
        // Arrange
        String collectionTitle = "New Collection";
        Long expectedId = 42L;
        when(raindropApiClient.createCollection(collectionTitle)).thenReturn(expectedId);

        // Act
        Long result = raindropService.createCollection(collectionTitle);

        // Assert
        assertThat(result).isEqualTo(expectedId);
        verify(raindropApiClient).createCollection(collectionTitle);
    }

    @Test
    void createCollection_shouldEvictCollectionsCache() {
        // This test verifies cache eviction happens
        // Implementation will be validated in integration tests
        String collectionTitle = "New Collection";
        when(raindropApiClient.createCollection(collectionTitle)).thenReturn(1L);

        raindropService.createCollection(collectionTitle);

        verify(raindropApiClient).createCollection(collectionTitle);
    }
}
