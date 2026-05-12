package org.bartram.vidtag.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcessedVideoEntryTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void jsonRoundtrip_preservesAllFields() throws Exception {
        ProcessedVideoEntry entry = new ProcessedVideoEntry(
                Instant.parse("2026-05-11T20:14:33Z"),
                Source.PLAYLIST,
                "How to swing a golf club",
                "https://www.youtube.com/watch?v=abc123",
                ProcessingStatus.SUCCESS,
                List.of("golf", "swing"),
                "Golf Tutorials");

        String json = mapper.writeValueAsString(entry);
        ProcessedVideoEntry roundtripped = mapper.readValue(json, ProcessedVideoEntry.class);

        assertThat(roundtripped).isEqualTo(entry);
    }

    @Test
    void jsonRoundtrip_handlesNullCollection() throws Exception {
        ProcessedVideoEntry entry = new ProcessedVideoEntry(
                Instant.parse("2026-05-11T20:14:33Z"),
                Source.UNSORTED,
                "x",
                "https://youtu.be/x",
                ProcessingStatus.SKIPPED,
                List.of(),
                null);

        String json = mapper.writeValueAsString(entry);
        ProcessedVideoEntry roundtripped = mapper.readValue(json, ProcessedVideoEntry.class);

        assertThat(roundtripped.collection()).isNull();
        assertThat(roundtripped).isEqualTo(entry);
    }

    @Test
    void timestampSerializesAsIso8601String() throws Exception {
        ProcessedVideoEntry entry = new ProcessedVideoEntry(
                Instant.parse("2026-05-11T20:14:33Z"),
                Source.PLAYLIST,
                "x",
                "https://www.youtube.com/watch?v=x",
                ProcessingStatus.SUCCESS,
                List.of(),
                "Videos");

        String json = mapper.writeValueAsString(entry);

        assertThat(json).contains("\"2026-05-11T20:14:33Z\"");
    }
}
