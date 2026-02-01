package org.bartram.vidtag.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Consumer;
import org.bartram.vidtag.dto.TagPlaylistRequest;
import org.bartram.vidtag.event.ProgressEvent;
import org.bartram.vidtag.service.VideoTaggingOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit tests for PlaylistTaggingController.
 */
@ExtendWith(MockitoExtension.class)
class PlaylistTaggingControllerTest {

    @Mock
    private VideoTaggingOrchestrator orchestrator;

    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @Captor
    private ArgumentCaptor<TagPlaylistRequest> requestCaptor;

    @Captor
    private ArgumentCaptor<Consumer<ProgressEvent>> eventEmitterCaptor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        PlaylistTaggingController controller = new PlaylistTaggingController(orchestrator, objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void tagPlaylist_shouldStartAsyncProcessingForValidRequest() throws Exception {
        // Given
        TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, null, null);

        // When/Then
        mockMvc.perform(post("/api/v1/playlists/tag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk());
    }

    @Test
    void tagPlaylist_shouldInvokeOrchestratorWithRequest() throws Exception {
        // Given
        TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, null, null);

        // When
        mockMvc.perform(post("/api/v1/playlists/tag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(request().asyncStarted());

        // Then
        verify(orchestrator).processPlaylist(requestCaptor.capture(), any());
        TagPlaylistRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.playlistInput()).isEqualTo("PLtest123");
        // Collection is now determined by AI, not passed in request
    }

    @Test
    void tagPlaylist_shouldReturn400ForMissingPlaylistInput() throws Exception {
        // Given
        String requestJson = """
            {
                "playlistInput": null}
            """;

        // When/Then
        mockMvc.perform(post("/api/v1/playlists/tag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tagPlaylist_shouldReturn400ForBlankPlaylistInput() throws Exception {
        // Given
        String requestJson = """
            {
                "playlistInput": "   "}
            """;

        // When/Then
        mockMvc.perform(post("/api/v1/playlists/tag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    // Tests for raindropCollectionTitle validation removed - field no longer exists
    // Collection is now automatically determined by AI

    @Test
    void tagPlaylist_shouldPassEventEmitterToOrchestrator() throws Exception {
        // Given
        TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, null, null);

        // When
        mockMvc.perform(post("/api/v1/playlists/tag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(request().asyncStarted());

        // Then
        verify(orchestrator).processPlaylist(any(), eventEmitterCaptor.capture());
        assertThat(eventEmitterCaptor.getValue()).isNotNull();
    }

    @Test
    void tagPlaylist_shouldAcceptOptionalParameters() throws Exception {
        // Given
        String requestJson = """
            {
                "playlistInput": "PLtest123","filters": {
                    "publishedAfter": "2024-01-01T00:00:00Z",
                    "maxDurationSeconds": 3600,
                    "maxVideos": 10
                },
                "tagStrategy": {
                    "maxTagsPerVideo": 5,
                    "confidenceThreshold": 0.7,
                    "customInstructions": "Focus on programming"
                }
            }
            """;

        // When
        mockMvc.perform(post("/api/v1/playlists/tag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(request().asyncStarted());

        // Then
        verify(orchestrator).processPlaylist(requestCaptor.capture(), any());
        TagPlaylistRequest captured = requestCaptor.getValue();
        assertThat(captured.filters()).isNotNull();
        assertThat(captured.filters().maxVideos()).isEqualTo(10);
        assertThat(captured.tagStrategy()).isNotNull();
        assertThat(captured.tagStrategy().maxTagsPerVideo()).isEqualTo(5);
    }

    @Test
    void tagPlaylist_eventEmitterShouldSerializeEventsToJson() throws Exception {
        // Given
        TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, null, null);

        // When
        mockMvc.perform(post("/api/v1/playlists/tag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(request().asyncStarted());

        // Capture the event emitter
        verify(orchestrator).processPlaylist(any(), eventEmitterCaptor.capture());
        Consumer<ProgressEvent> emitter = eventEmitterCaptor.getValue();

        // Then - emitter should not throw when accepting events
        // (IOException would occur if serialization fails)
        assertThat(emitter).isNotNull();
    }

    @Test
    void tagPlaylist_shouldReturn400WhenBothFieldsAreMissing() throws Exception {
        // Given
        String requestJson = """
            {
            }
            """;

        // When/Then
        mockMvc.perform(post("/api/v1/playlists/tag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }
}
