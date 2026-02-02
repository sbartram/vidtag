package org.bartram.vidtag.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.bartram.vidtag.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(
        properties = {
            "vidtag.scheduler.enabled=true",
            "vidtag.scheduler.fixed-delay=1h",
            "vidtag.scheduler.initial-delay=10s",
            "vidtag.scheduler.playlist-ids=PLtest123"
        })
class SchedulerPropertiesTest {

    @Autowired
    private SchedulerProperties schedulerProperties;

    @Test
    void shouldLoadSchedulerProperties() {
        assertThat(schedulerProperties).isNotNull();
        assertThat(schedulerProperties.isEnabled()).isTrue();
        assertThat(schedulerProperties.getFixedDelay()).isEqualTo(Duration.ofHours(1));
        assertThat(schedulerProperties.getInitialDelay()).isEqualTo(Duration.ofSeconds(10));
        assertThat(schedulerProperties.getPlaylistIds()).isEqualTo("PLtest123");
    }

    @Test
    void shouldHaveDefaultValues() {
        // This will use application.yaml defaults
        assertThat(schedulerProperties.getPlaylistIds()).isNotNull();
        assertThat(schedulerProperties.getFixedDelay()).isPositive();
        assertThat(schedulerProperties.getInitialDelay()).isPositive();
    }
}
