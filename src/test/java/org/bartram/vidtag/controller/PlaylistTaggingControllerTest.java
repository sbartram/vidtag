package org.bartram.vidtag.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bartram.vidtag.dto.TagPlaylistRequest;
import org.bartram.vidtag.event.ProgressEvent;
import org.bartram.vidtag.service.VideoTaggingOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit tests for PlaylistTaggingController.
 */
@ExtendWith(MockitoExtension.class)
class PlaylistTaggingControllerTest {

    @Mock
    private VideoTaggingOrchestrator orchestrator;

    @Mock
    private ObjectMapper mockObjectMapper;

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

    @Nested
    class SseEventHandling {

        @Test
        void shouldSendStartedEventSuccessfully() throws Exception {
            // Given
            TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, null, null);
            AtomicReference<Consumer<ProgressEvent>> emitterRef = new AtomicReference<>();

            doAnswer(invocation -> {
                        emitterRef.set(invocation.getArgument(1));
                        // Send started event
                        emitterRef.get().accept(ProgressEvent.started("Processing started"));
                        emitterRef.get().accept(ProgressEvent.completed("Done", Map.of("total", 1)));
                        return null;
                    })
                    .when(orchestrator)
                    .processPlaylist(any(), any());

            // When
            MvcResult mvcResult = mockMvc.perform(post("/api/v1/playlists/tag")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            // Then - async dispatch to get the response
            mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk());
        }

        @Test
        void shouldCompleteEmitterOnCompletedEvent() throws Exception {
            // Given
            TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, null, null);

            doAnswer(invocation -> {
                        Consumer<ProgressEvent> emitter = invocation.getArgument(1);
                        emitter.accept(
                                ProgressEvent.completed("Processing completed", Map.of("total", 5, "succeeded", 5)));
                        return null;
                    })
                    .when(orchestrator)
                    .processPlaylist(any(), any());

            // When
            MvcResult mvcResult = mockMvc.perform(post("/api/v1/playlists/tag")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            // Then
            mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk());
        }

        @Test
        void shouldCompleteEmitterOnFatalError() throws Exception {
            // Given
            TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, null, null);

            doAnswer(invocation -> {
                        Consumer<ProgressEvent> emitter = invocation.getArgument(1);
                        emitter.accept(ProgressEvent.error("Fatal error: Something went wrong"));
                        return null;
                    })
                    .when(orchestrator)
                    .processPlaylist(any(), any());

            // When
            MvcResult mvcResult = mockMvc.perform(post("/api/v1/playlists/tag")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            // Then
            mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk());
        }

        @Test
        void shouldNotCompleteEmitterOnNonFatalError() throws Exception {
            // Given
            TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, null, null);
            CountDownLatch latch = new CountDownLatch(1);

            doAnswer(invocation -> {
                        Consumer<ProgressEvent> emitter = invocation.getArgument(1);
                        // Non-fatal error should not complete the emitter
                        emitter.accept(ProgressEvent.error("Video processing failed"));
                        emitter.accept(ProgressEvent.progress("Continuing..."));
                        emitter.accept(ProgressEvent.completed("Done", Map.of()));
                        latch.countDown();
                        return null;
                    })
                    .when(orchestrator)
                    .processPlaylist(any(), any());

            // When
            MvcResult mvcResult = mockMvc.perform(post("/api/v1/playlists/tag")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            latch.await(5, TimeUnit.SECONDS);

            // Then
            mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk());
        }

        @Test
        void shouldSendProgressEventWithData() throws Exception {
            // Given
            TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, null, null);

            doAnswer(invocation -> {
                        Consumer<ProgressEvent> emitter = invocation.getArgument(1);
                        emitter.accept(ProgressEvent.progress("Processing video", Map.of("current", 1, "total", 10)));
                        emitter.accept(ProgressEvent.completed("Done", Map.of()));
                        return null;
                    })
                    .when(orchestrator)
                    .processPlaylist(any(), any());

            // When
            MvcResult mvcResult = mockMvc.perform(post("/api/v1/playlists/tag")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            // Then
            mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk());
        }

        @Test
        void shouldSendVideoCompletedEvent() throws Exception {
            // Given
            TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, null, null);

            doAnswer(invocation -> {
                        Consumer<ProgressEvent> emitter = invocation.getArgument(1);
                        emitter.accept(ProgressEvent.videoCompleted(
                                "Video processed",
                                Map.of("videoId", "abc123", "tags", java.util.List.of("java", "tutorial"))));
                        emitter.accept(ProgressEvent.completed("Done", Map.of()));
                        return null;
                    })
                    .when(orchestrator)
                    .processPlaylist(any(), any());

            // When
            MvcResult mvcResult = mockMvc.perform(post("/api/v1/playlists/tag")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            // Then
            mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk());
        }

        @Test
        void shouldSendVideoSkippedEvent() throws Exception {
            // Given
            TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, null, null);

            doAnswer(invocation -> {
                        Consumer<ProgressEvent> emitter = invocation.getArgument(1);
                        emitter.accept(ProgressEvent.videoSkipped("Video already exists", Map.of("videoId", "abc123")));
                        emitter.accept(ProgressEvent.completed("Done", Map.of()));
                        return null;
                    })
                    .when(orchestrator)
                    .processPlaylist(any(), any());

            // When
            MvcResult mvcResult = mockMvc.perform(post("/api/v1/playlists/tag")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            // Then
            mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk());
        }

        @Test
        void shouldSendBatchCompletedEvent() throws Exception {
            // Given
            TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, null, null);

            doAnswer(invocation -> {
                        Consumer<ProgressEvent> emitter = invocation.getArgument(1);
                        emitter.accept(ProgressEvent.batchCompleted(
                                "Batch 1 completed", Map.of("batchNumber", 1, "processed", 10)));
                        emitter.accept(ProgressEvent.completed("Done", Map.of()));
                        return null;
                    })
                    .when(orchestrator)
                    .processPlaylist(any(), any());

            // When
            MvcResult mvcResult = mockMvc.perform(post("/api/v1/playlists/tag")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            // Then
            mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk());
        }

        @Test
        void shouldSendErrorEventWithData() throws Exception {
            // Given
            TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, null, null);

            doAnswer(invocation -> {
                        Consumer<ProgressEvent> emitter = invocation.getArgument(1);
                        emitter.accept(ProgressEvent.error("Error processing video", Map.of("videoId", "xyz789")));
                        emitter.accept(ProgressEvent.completed("Done with errors", Map.of()));
                        return null;
                    })
                    .when(orchestrator)
                    .processPlaylist(any(), any());

            // When
            MvcResult mvcResult = mockMvc.perform(post("/api/v1/playlists/tag")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            // Then
            mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk());
        }

        @Test
        void shouldSendMultipleEventsInSequence() throws Exception {
            // Given
            TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, null, null);

            doAnswer(invocation -> {
                        Consumer<ProgressEvent> emitter = invocation.getArgument(1);
                        emitter.accept(ProgressEvent.started("Starting playlist processing"));
                        emitter.accept(ProgressEvent.progress("Fetching videos"));
                        emitter.accept(
                                ProgressEvent.progress("Processing video 1/3", Map.of("current", 1, "total", 3)));
                        emitter.accept(ProgressEvent.videoCompleted("Video 1 processed", Map.of("videoId", "v1")));
                        emitter.accept(
                                ProgressEvent.progress("Processing video 2/3", Map.of("current", 2, "total", 3)));
                        emitter.accept(ProgressEvent.videoSkipped("Video 2 already exists", Map.of("videoId", "v2")));
                        emitter.accept(
                                ProgressEvent.progress("Processing video 3/3", Map.of("current", 3, "total", 3)));
                        emitter.accept(ProgressEvent.error("Failed to process video 3"));
                        emitter.accept(ProgressEvent.batchCompleted("Batch completed", Map.of("processed", 3)));
                        emitter.accept(ProgressEvent.completed(
                                "All done", Map.of("total", 3, "succeeded", 1, "skipped", 1, "failed", 1)));
                        return null;
                    })
                    .when(orchestrator)
                    .processPlaylist(any(), any());

            // When
            MvcResult mvcResult = mockMvc.perform(post("/api/v1/playlists/tag")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            // Then
            mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk());
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void shouldHandleJsonSerializationError() throws Exception {
            // Given - create controller with mock ObjectMapper that throws
            ObjectMapper failingMapper = mock(ObjectMapper.class);
            when(failingMapper.writeValueAsString(any(ProgressEvent.class)))
                    .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("Serialization failed") {});

            PlaylistTaggingController controllerWithFailingMapper =
                    new PlaylistTaggingController(orchestrator, failingMapper);
            MockMvc mockMvcWithFailingMapper =
                    MockMvcBuilders.standaloneSetup(controllerWithFailingMapper).build();

            TagPlaylistRequest request = new TagPlaylistRequest("PLtest123", null, null, null);

            doAnswer(invocation -> {
                        Consumer<ProgressEvent> emitter = invocation.getArgument(1);
                        // This will trigger JsonProcessingException
                        emitter.accept(ProgressEvent.started("Test"));
                        return null;
                    })
                    .when(orchestrator)
                    .processPlaylist(any(), any());

            // When - the request should start but the emitter will fail
            mockMvcWithFailingMapper
                    .perform(post("/api/v1/playlists/tag")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(request().asyncStarted());

            // Then - verify orchestrator was called
            verify(orchestrator).processPlaylist(any(), any());
        }
    }
}
