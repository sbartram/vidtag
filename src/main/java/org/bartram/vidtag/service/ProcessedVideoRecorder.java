package org.bartram.vidtag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.vidtag.event.VideoProcessedEvent;
import org.bartram.vidtag.model.ProcessedVideoEntry;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Records processed videos to a capped Redis list and reads them back for the {@code GET
 * /processed} view. Single source of truth for the {@code vidtag:processed:recent} key.
 *
 * <p>Recording failures must not propagate — they are logged and swallowed so that a Redis outage
 * cannot fail a pipeline.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProcessedVideoRecorder {

    static final String KEY = "vidtag:processed:recent";
    static final long MAX_INDEX = 99L; // keep 100 most recent entries

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /**
     * Async listener that records the event's entry. Calls {@link #record(ProcessedVideoEntry)} so
     * that tests can bypass the async proxy by invoking {@code record} directly.
     */
    @Async
    @EventListener
    public void onVideoProcessed(VideoProcessedEvent event) {
        record(event.entry());
    }

    /**
     * Writes one entry to the head of the Redis list and trims to the cap. Package-private to allow
     * synchronous unit tests.
     */
    void record(ProcessedVideoEntry entry) {
        try {
            String json = objectMapper.writeValueAsString(entry);
            redis.opsForList().leftPush(KEY, json);
            redis.opsForList().trim(KEY, 0, MAX_INDEX);
        } catch (Exception e) {
            log.atWarn()
                    .setMessage("Failed to record processed video: {}")
                    .addArgument(entry.url())
                    .setCause(e)
                    .log();
        }
    }

    /**
     * Returns the recorded entries, newest first. Returns an empty list on any read failure (Redis
     * down, malformed JSON, etc.).
     */
    public List<ProcessedVideoEntry> recent() {
        try {
            List<String> raw = redis.opsForList().range(KEY, 0, -1);
            if (raw == null) {
                return List.of();
            }
            return raw.stream().map(this::deserialize).filter(Objects::nonNull).toList();
        } catch (Exception e) {
            log.atWarn()
                    .setMessage("Failed to read processed videos list")
                    .setCause(e)
                    .log();
            return List.of();
        }
    }

    private ProcessedVideoEntry deserialize(String json) {
        try {
            return objectMapper.readValue(json, ProcessedVideoEntry.class);
        } catch (Exception e) {
            log.atWarn()
                    .setMessage("Skipping malformed processed video entry")
                    .setCause(e)
                    .log();
            return null;
        }
    }
}
