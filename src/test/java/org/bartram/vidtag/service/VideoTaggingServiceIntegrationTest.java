package org.bartram.vidtag.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.bartram.vidtag.model.RaindropTag;
import org.bartram.vidtag.model.TagStrategy;
import org.bartram.vidtag.model.TagWithConfidence;
import org.bartram.vidtag.model.VideoMetadata;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test for VideoTaggingService with real Claude API.
 *
 * Integration test - requires API key.
 * Set environment variable API_KEY_VIDTAG to run this test.
 */
@SpringBootTest
@Disabled("Integration test - requires API key")
class VideoTaggingServiceIntegrationTest {

    @Autowired
    private VideoTaggingService videoTaggingService;

    @Test
    void generateTags_withRealApi_shouldReturnTags() {
        // Given
        VideoMetadata video = new VideoMetadata(
                "dQw4w9WgXcQ",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "Spring Boot Tutorial for Beginners",
                "Learn how to build REST APIs with Spring Boot. "
                        + "This tutorial covers dependency injection, JPA, and creating RESTful endpoints.",
                Instant.now(),
                1800);
        List<RaindropTag> existingTags = List.of(
                new RaindropTag("java"),
                new RaindropTag("spring"),
                new RaindropTag("tutorial"),
                new RaindropTag("programming"));
        TagStrategy strategy = new TagStrategy(5, 0.7, "Focus on technology and programming topics");

        // When
        List<TagWithConfidence> result = videoTaggingService.generateTags(video, existingTags, strategy);

        // Then
        assertThat(result).isNotEmpty();
        assertThat(result).hasSizeLessThanOrEqualTo(5);
        assertThat(result).allMatch(tag -> tag.confidence() >= 0.7);
        assertThat(result).allMatch(tag -> tag.tag() != null && !tag.tag().isBlank());
        assertThat(result).allMatch(tag -> tag.isExisting() != null);

        // Log results for manual verification
        System.out.println("Generated tags:");
        result.forEach(tag -> System.out.printf(
                "  - %s (confidence: %.2f, existing: %s)%n", tag.tag(), tag.confidence(), tag.isExisting()));
    }

    @Test
    void generateTags_withEmptyExistingTags_shouldGenerateNewTags() {
        // Given
        VideoMetadata video = new VideoMetadata(
                "test123",
                "https://www.youtube.com/watch?v=test123",
                "Machine Learning with Python",
                "Introduction to neural networks and deep learning using TensorFlow and PyTorch",
                Instant.now(),
                2400);
        List<RaindropTag> existingTags = List.of();
        TagStrategy strategy = new TagStrategy(8, 0.6, null);

        // When
        List<TagWithConfidence> result = videoTaggingService.generateTags(video, existingTags, strategy);

        // Then
        assertThat(result).isNotEmpty();
        assertThat(result).hasSizeLessThanOrEqualTo(8);
        assertThat(result).allMatch(tag -> tag.isExisting() != null);

        System.out.println("Generated tags for ML video:");
        result.forEach(tag -> System.out.printf("  - %s (confidence: %.2f)%n", tag.tag(), tag.confidence()));
    }
}
