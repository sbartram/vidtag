package org.bartram.vidtag.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.bartram.vidtag.TestcontainersConfiguration;
import org.bartram.vidtag.model.ProcessedVideoEntry;
import org.bartram.vidtag.model.ProcessingStatus;
import org.bartram.vidtag.model.Source;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProcessedVideoRecorderTest {

    @Autowired
    private ProcessedVideoRecorder recorder;

    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanUp() {
        redis.delete(ProcessedVideoRecorder.KEY);
    }

    @Test
    void record_thenRecent_returnsEntry() {
        ProcessedVideoEntry entry = sample("a");

        recorder.record(entry);

        assertThat(recorder.recent()).containsExactly(entry);
    }

    @Test
    void record101Entries_recentReturns100NewestFirstOldestDropped() {
        for (int i = 0; i < 101; i++) {
            recorder.record(sample("v" + i));
        }

        List<ProcessedVideoEntry> recent = recorder.recent();

        assertThat(recent).hasSize(100);
        assertThat(recent.get(0).title()).isEqualTo("v100"); // newest at head
        assertThat(recent.get(99).title()).isEqualTo("v1"); // v0 was trimmed off
    }

    @Test
    void recent_onMissingKey_returnsEmptyList() {
        // @AfterEach deletes the key; nothing has been written this test
        assertThat(recorder.recent()).isEmpty();
    }

    @Test
    void recent_withMalformedJsonElement_skipsThatElementOnly() {
        // Manually push a junk string, then a valid entry
        redis.opsForList().leftPush(ProcessedVideoRecorder.KEY, "{not valid json");
        recorder.record(sample("good"));

        List<ProcessedVideoEntry> recent = recorder.recent();

        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).title()).isEqualTo("good");
    }

    private ProcessedVideoEntry sample(String title) {
        return new ProcessedVideoEntry(
                Instant.parse("2026-05-11T20:14:33Z"),
                Source.PLAYLIST,
                title,
                "https://www.youtube.com/watch?v=" + title,
                ProcessingStatus.SUCCESS,
                List.of("golf"),
                "Videos");
    }
}
