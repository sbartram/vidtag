package org.bartram.vidtag.dto;

import org.bartram.vidtag.model.TagStrategy;
import org.bartram.vidtag.model.Verbosity;
import org.bartram.vidtag.model.VideoFilters;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TagPlaylistRequestTest {

    @Test
    void testValidRequestCreation() {
        // Given
        String playlistInput = "PLtest123";
        VideoFilters filters = new VideoFilters(
            Instant.parse("2024-01-01T00:00:00Z"),
            3600,
            100
        );
        TagStrategy tagStrategy = new TagStrategy(
            5,
            0.7,
            "Focus on technical content"
        );
        Verbosity verbosity = Verbosity.STANDARD;

        // When
        TagPlaylistRequest request = new TagPlaylistRequest(
            playlistInput,
            filters,
            tagStrategy,
            verbosity
        );

        // Then
        assertNotNull(request);
        assertEquals(playlistInput, request.playlistInput());
        assertEquals(filters, request.filters());
        assertEquals(tagStrategy, request.tagStrategy());
        assertEquals(verbosity, request.verbosity());
    }

    @Test
    void testRequestWithNullFilters() {
        // Given
        String playlistInput = "PLtest123";
        TagStrategy tagStrategy = new TagStrategy(5, 0.7, null);
        Verbosity verbosity = Verbosity.STANDARD;

        // When
        TagPlaylistRequest request = new TagPlaylistRequest(
            playlistInput,
            null,
            tagStrategy,
            verbosity
        );

        // Then
        assertNotNull(request);
        assertNull(request.filters());
    }

    @Test
    void testRequestWithNullStrategy() {
        // Given
        String playlistInput = "PLtest123";
        VideoFilters filters = new VideoFilters(null, null, null);
        Verbosity verbosity = Verbosity.MINIMAL;

        // When
        TagPlaylistRequest request = new TagPlaylistRequest(
            playlistInput,
            filters,
            null,
            verbosity
        );

        // Then
        assertNotNull(request);
        assertNull(request.tagStrategy());
    }

    @Test
    void testRecordEquality() {
        // Given
        TagStrategy tagStrategy = new TagStrategy(5, 0.7, null);
        VideoFilters filters = new VideoFilters(null, null, null);
        Verbosity verbosity = Verbosity.DETAILED;

        TagPlaylistRequest request1 = new TagPlaylistRequest(
            "PLtest123",
            filters,
            tagStrategy,
            verbosity
        );

        TagPlaylistRequest request2 = new TagPlaylistRequest(
            "PLtest123",
            filters,
            tagStrategy,
            verbosity
        );

        // Then
        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());
    }
}
