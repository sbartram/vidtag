package org.bartram.vidtag.client.impl;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistListResponse;
import org.bartram.vidtag.exception.ExternalServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YouTubeApiClientImplSearchTest {

    @Mock
    private YouTube youtube;

    @Mock
    private YouTube.Playlists playlists;

    @Mock
    private YouTube.Playlists.List listRequest;

    private YouTubeApiClientImpl client;

    @BeforeEach
    void setUp() {
        client = new YouTubeApiClientImpl(youtube, "test-api-key");
    }

    @Test
    void shouldFindPlaylistByName() throws IOException {
        Playlist playlist = new Playlist();
        playlist.setId("PLxyz123");
        playlist.setSnippet(new com.google.api.services.youtube.model.PlaylistSnippet().setTitle("tag"));

        PlaylistListResponse response = new PlaylistListResponse();
        response.setItems(List.of(playlist));

        when(youtube.playlists()).thenReturn(playlists);
        when(playlists.list(List.of("snippet"))).thenReturn(listRequest);
        when(listRequest.setMine(true)).thenReturn(listRequest);
        when(listRequest.setMaxResults(50L)).thenReturn(listRequest);
        when(listRequest.execute()).thenReturn(response);

        String playlistId = client.findPlaylistByName("tag");

        assertThat(playlistId).isEqualTo("PLxyz123");
    }

    @Test
    void shouldReturnNullWhenPlaylistNotFound() throws IOException {
        PlaylistListResponse response = new PlaylistListResponse();
        response.setItems(Collections.emptyList());

        when(youtube.playlists()).thenReturn(playlists);
        when(playlists.list(List.of("snippet"))).thenReturn(listRequest);
        when(listRequest.setMine(true)).thenReturn(listRequest);
        when(listRequest.setMaxResults(50L)).thenReturn(listRequest);
        when(listRequest.execute()).thenReturn(response);

        String playlistId = client.findPlaylistByName("nonexistent");

        assertThat(playlistId).isNull();
    }

    @Test
    void shouldThrowExceptionOnApiError() throws IOException {
        when(youtube.playlists()).thenReturn(playlists);
        when(playlists.list(List.of("snippet"))).thenReturn(listRequest);
        when(listRequest.setMine(true)).thenReturn(listRequest);
        when(listRequest.setMaxResults(50L)).thenReturn(listRequest);
        when(listRequest.execute()).thenThrow(new IOException("API error"));

        assertThatThrownBy(() -> client.findPlaylistByName("tag"))
            .isInstanceOf(ExternalServiceException.class)
            .hasMessageContaining("Failed to search for playlist");
    }
}
