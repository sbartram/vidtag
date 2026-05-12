package org.bartram.vidtag.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.bartram.vidtag.TestcontainersConfiguration;
import org.bartram.vidtag.event.VideoProcessedEvent;
import org.bartram.vidtag.model.ProcessedVideoEntry;
import org.bartram.vidtag.model.ProcessingStatus;
import org.bartram.vidtag.model.Source;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VideoProcessedEventIT {

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private ProcessedVideoRecorder recorder;

    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanUp() {
        redis.delete(ProcessedVideoRecorder.KEY);
    }

    @Test
    void publishedEvent_reachesAsyncListener_andLandsInRedis() throws Exception {
        ProcessedVideoEntry entry = new ProcessedVideoEntry(
                Instant.parse("2026-05-11T20:14:33Z"),
                Source.PLAYLIST,
                "integration",
                "https://www.youtube.com/watch?v=int",
                ProcessingStatus.SUCCESS,
                List.of("test"),
                "Videos");

        publisher.publishEvent(new VideoProcessedEvent(entry));

        // Poll up to 2 seconds for the async listener to land the entry in Redis.
        List<ProcessedVideoEntry> recent = waitFor(
                () -> {
                    List<ProcessedVideoEntry> r = recorder.recent();
                    return r.isEmpty() ? null : r;
                },
                2_000);

        assertThat(recent).containsExactly(entry);
    }

    private <T> T waitFor(java.util.function.Supplier<T> s, long maxMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + maxMillis;
        while (System.currentTimeMillis() < deadline) {
            T value = s.get();
            if (value != null) {
                return value;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met within " + maxMillis + "ms");
    }
}
