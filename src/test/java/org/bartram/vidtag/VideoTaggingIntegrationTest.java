package org.bartram.vidtag;

import org.bartram.vidtag.client.MockRaindropApiClient;
import org.bartram.vidtag.client.MockYouTubeApiClient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the video tagging workflow.
 * Uses Testcontainers for real Redis and mock clients for external APIs.
 *
 * <p>This test is disabled by default as it requires:
 * <ul>
 *   <li>Docker to be running for Testcontainers</li>
 *   <li>API_KEY_VIDTAG environment variable for Claude AI</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import({MockYouTubeApiClient.class, MockRaindropApiClient.class})
@Disabled("Integration test - requires API key for Claude AI")
class VideoTaggingIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Test
    void tagPlaylist_withValidRequest_returnsOk() throws Exception {
        String requestBody = """
            {
                "playlistInput": "PLtest123","verbosity": "STANDARD"
            }
            """;

        mockMvc.perform(post("/api/v1/playlists/tag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());
    }

    @Test
    void tagPlaylist_withFilters_returnsOk() throws Exception {
        String requestBody = """
            {
                "playlistInput": "https://www.youtube.com/playlist?list=PLtest456","filters": {
                    "maxDuration": 900,
                    "maxVideos": 5
                },
                "tagStrategy": {
                    "maxTagsPerVideo": 10
                },
                "verbosity": "DETAILED"
            }
            """;

        mockMvc.perform(post("/api/v1/playlists/tag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());
    }

    @Test
    void tagPlaylist_withMissingPlaylistInput_returnsBadRequest() throws Exception {
        String requestBody = """
            {}
            """;

        mockMvc.perform(post("/api/v1/playlists/tag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tagPlaylist_withMissingCollectionTitle_returnsBadRequest() throws Exception {
        String requestBody = """
            {
                "playlistInput": "PLtest789"
            }
            """;

        mockMvc.perform(post("/api/v1/playlists/tag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }
}
