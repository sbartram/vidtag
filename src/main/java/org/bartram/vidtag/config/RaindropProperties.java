package org.bartram.vidtag.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Raindrop.io integration.
 */
@Getter
@Setter
@Accessors(fluent = false)
@Component
@ConfigurationProperties(prefix = "vidtag.raindrop")
public class RaindropProperties {

    /**
     * Fallback collection name to use when AI confidence is low or errors occur.
     */
    private String fallbackCollection = "Videos";

    /**
     * TTL for playlist → collection mapping cache.
     */
    private Duration collectionCacheTtl = Duration.ofHours(24);

    /**
     * TTL for user's collections list cache.
     */
    private Duration collectionsListCacheTtl = Duration.ofHours(1);
}
