package org.bartram.vidtag.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.vidtag.dto.TagPlaylistRequest;
import org.bartram.vidtag.dto.error.ErrorResponse;
import org.bartram.vidtag.dto.error.ValidationErrorResponse;
import org.bartram.vidtag.event.ProgressEvent;
import org.bartram.vidtag.service.VideoTaggingOrchestrator;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * REST controller for playlist tagging operations.
 * Provides SSE (Server-Sent Events) streaming for real-time progress updates.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/playlists")
@RequiredArgsConstructor
@Tag(name = "Playlist Tagging", description = "AI-powered video tagging for YouTube playlists")
public class PlaylistTaggingController {

    private static final long SSE_TIMEOUT = 3600000L; // 1 hour

    private final VideoTaggingOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

    @Operation(summary = "Tag YouTube playlist videos with AI", description = """
            Analyzes videos from a YouTube playlist using Claude AI and creates bookmarks
            in Raindrop.io with intelligent tags. Returns a Server-Sent Events (SSE) stream
            for real-time progress updates.

            ### Process Flow:
            1. Fetch videos from YouTube playlist (with optional filters)
            2. Resolve Raindrop collection by title
            3. Fetch existing Raindrop tags for intelligent selection
            4. For each video:
               - Generate tags using Claude AI
               - Prefer existing tags, suggest new ones only when confident
               - Create bookmark in Raindrop with selected tags
            5. Stream progress events in real-time

            ### SSE Event Types:
            - `started` - Processing began
            - `progress` - Status update
            - `video_completed` - Video processed successfully
            - `video_skipped` - Video already exists (duplicate)
            - `batch_completed` - Batch of videos completed
            - `error` - Error occurred
            - `completed` - Processing finished with summary
            """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "SSE stream started successfully. Events will be sent as processing progresses.",
                        content =
                                @Content(
                                        mediaType = "text/event-stream",
                                        examples = @ExampleObject(name = "SSE Events", value = """
                        event:started
                        data:{"eventType":"started","message":"Processing playlist: PLxxx","data":null}

                        event:video_completed
                        data:{"eventType":"video_completed","message":"Video processed","data":{...}}

                        event:completed
                        data:{"eventType":"completed","message":"Processing completed","data":{"totalVideos":10,"succeeded":9,"skipped":1,"failed":0}}
                        """))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error - invalid request parameters",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ValidationErrorResponse.class),
                                        examples = @ExampleObject(value = """
                        {
                          "errorCode": "VALIDATION_FAILED",
                          "message": "Request validation failed",
                          "status": 400,
                          "validationErrors": [
                            {"field": "playlistInput", "message": "playlistInput is required"}
                          ]
                        }
                        """))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Resource not found - collection doesn't exist in Raindrop",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "503",
                        description = "External service unavailable - YouTube, Raindrop, or Claude API failure",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping(value = "/tag", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> tagPlaylist(@Validated @RequestBody TagPlaylistRequest request) {
        log.info("Received playlist tagging request for: {}", request.playlistInput());

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        // Setup emitter callbacks
        emitter.onCompletion(() -> log.info("SSE connection completed for playlist: {}", request.playlistInput()));

        emitter.onTimeout(() -> {
            log.warn("SSE connection timed out for playlist: {}", request.playlistInput());
            emitter.complete();
        });

        emitter.onError(throwable -> {
            log.error("SSE error for playlist {}: {}", request.playlistInput(), throwable.getMessage(), throwable);
            emitter.completeWithError(throwable);
        });

        // Start async processing
        orchestrator.processPlaylist(request, event -> sendEvent(emitter, event));

        return ResponseEntity.ok(emitter);
    }

    /**
     * Sends a progress event through the SSE emitter.
     *
     * @param emitter the SSE emitter
     * @param event the progress event to send
     */
    private void sendEvent(SseEmitter emitter, ProgressEvent event) {
        try {
            String jsonData = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event().name(event.eventType()).data(jsonData, MediaType.APPLICATION_JSON));

            // Complete emitter on terminal events
            if ("completed".equals(event.eventType())
                    || ("error".equals(event.eventType()) && event.message().startsWith("Fatal"))) {
                emitter.complete();
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event: {}", e.getMessage(), e);
            emitter.completeWithError(e);
        } catch (IOException e) {
            log.error("Failed to send SSE event: {}", e.getMessage(), e);
            emitter.completeWithError(e);
        }
    }
}
