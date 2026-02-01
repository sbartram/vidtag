package org.bartram.vidtag.client;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.bartram.vidtag.model.VideoMetadata;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration providing a mock YouTubeApiClient for integration tests.
 * Returns fixed test data for predictable testing scenarios.
 */
@TestConfiguration
public class MockYouTubeApiClient {

    /**
     * Provides a mock YouTubeApiClient that returns fixed test data.
     * Returns 2 VideoMetadata objects with different publishedAt dates and durations
     * to allow testing of filter functionality.
     *
     * @return mock YouTubeApiClient implementation
     */
    @Bean
    @Primary
    public YouTubeApiClient youTubeApiClient() {
        return new YouTubeApiClient() {
            @Override
            public List<VideoMetadata> getPlaylistVideos(String playlistId) {
                return List.of(
                        new VideoMetadata(
                                "video1",
                                "https://www.youtube.com/watch?v=video1",
                                "Spring Boot Tutorial - Getting Started",
                                "Learn the basics of Spring Boot framework including auto-configuration, starters, and building REST APIs.",
                                Instant.now().minus(7, ChronoUnit.DAYS),
                                600 // 10 minutes
                                ),
                        new VideoMetadata(
                                "video2",
                                "https://www.youtube.com/watch?v=video2",
                                "Java 21 New Features Overview",
                                "Explore the new features in Java 21 including virtual threads, pattern matching, and record patterns.",
                                Instant.now().minus(30, ChronoUnit.DAYS),
                                1800 // 30 minutes
                                ));
            }

            @Override
            public String findPlaylistByName(String playlistName) {
                return null;
            }
        };
    }
}
