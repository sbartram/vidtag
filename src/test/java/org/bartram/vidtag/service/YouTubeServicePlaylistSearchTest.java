package org.bartram.vidtag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.bartram.vidtag.client.YouTubeApiClient;
import org.bartram.vidtag.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class YouTubeServicePlaylistSearchTest {

    @Mock
    private YouTubeApiClient youtubeApiClient;

    @InjectMocks
    private YouTubeService youtubeService;

    @Test
    void shouldFindPlaylistIdByName() {
        when(youtubeApiClient.findPlaylistByName("tag")).thenReturn("PLxyz123");

        String playlistId = youtubeService.findPlaylistByName("tag");

        assertThat(playlistId).isEqualTo("PLxyz123");
    }

    @Test
    void shouldThrowExceptionWhenPlaylistNotFound() {
        when(youtubeApiClient.findPlaylistByName("nonexistent")).thenReturn(null);

        assertThatThrownBy(() -> youtubeService.findPlaylistByName("nonexistent"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Playlist not found: nonexistent");
    }
}
