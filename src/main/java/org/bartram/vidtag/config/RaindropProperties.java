package org.bartram.vidtag.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Raindrop.io integration.
 */
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

    public String getFallbackCollection() {
        return fallbackCollection;
    }

    public void setFallbackCollection(String fallbackCollection) {
        this.fallbackCollection = fallbackCollection;
    }

    public Duration getCollectionCacheTtl() {
        return collectionCacheTtl;
    }

    public void setCollectionCacheTtl(Duration collectionCacheTtl) {
        this.collectionCacheTtl = collectionCacheTtl;
    }

    public Duration getCollectionsListCacheTtl() {
        return collectionsListCacheTtl;
    }

    public void setCollectionsListCacheTtl(Duration collectionsListCacheTtl) {
        this.collectionsListCacheTtl = collectionsListCacheTtl;
    }
}
