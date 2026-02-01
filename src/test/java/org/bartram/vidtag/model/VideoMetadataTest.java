package org.bartram.vidtag.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class VideoMetadataTest {

    @Test
    void testValidVideoMetadataCreation() {
        // Given
        String videoId = "dQw4w9WgXcQ";
        String url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
        String title = "Test Video";
        String description = "A test video description";
        Instant publishedAt = Instant.parse("2024-01-15T10:30:00Z");
        Integer duration = 222;

        // When
        VideoMetadata metadata = new VideoMetadata(videoId, url, title, description, publishedAt, duration);

        // Then
        assertNotNull(metadata);
        assertEquals(videoId, metadata.videoId());
        assertEquals(url, metadata.url());
        assertEquals(title, metadata.title());
        assertEquals(description, metadata.description());
        assertEquals(publishedAt, metadata.publishedAt());
        assertEquals(duration, metadata.duration());
    }

    @Test
    void testVideoMetadataWithNullOptionalFields() {
        // Given
        String videoId = "dQw4w9WgXcQ";
        String url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
        String title = "Test Video";

        // When
        VideoMetadata metadata = new VideoMetadata(
                videoId,
                url,
                title,
                null, // description can be null
                null, // publishedAt can be null
                null // duration can be null
                );

        // Then
        assertNotNull(metadata);
        assertEquals(videoId, metadata.videoId());
        assertEquals(url, metadata.url());
        assertEquals(title, metadata.title());
        assertNull(metadata.description());
        assertNull(metadata.publishedAt());
        assertNull(metadata.duration());
    }

    @Test
    void testRecordEquality() {
        // Given
        Instant publishedAt = Instant.parse("2024-01-15T10:30:00Z");
        VideoMetadata metadata1 = new VideoMetadata(
                "dQw4w9WgXcQ",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "Test Video",
                "Description",
                publishedAt,
                300);

        VideoMetadata metadata2 = new VideoMetadata(
                "dQw4w9WgXcQ",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "Test Video",
                "Description",
                publishedAt,
                300);

        // Then
        assertEquals(metadata1, metadata2);
        assertEquals(metadata1.hashCode(), metadata2.hashCode());
    }
}
