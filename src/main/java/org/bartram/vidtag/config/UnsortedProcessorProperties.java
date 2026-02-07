package org.bartram.vidtag.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the unsorted bookmark processor.
 */
@Getter
@Setter
@Accessors(fluent = false)
@Component
@ConfigurationProperties(prefix = "vidtag.unsorted-processor")
public class UnsortedProcessorProperties {

    /**
     * Enable or disable the unsorted bookmark processor.
     */
    private boolean enabled = false;

    /**
     * Fixed delay between processor executions.
     */
    private Duration fixedDelay = Duration.ofHours(1);

    /**
     * Initial delay before the first execution.
     */
    private Duration initialDelay = Duration.ofSeconds(30);
}
