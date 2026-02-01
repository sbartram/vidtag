package org.bartram.vidtag.config;

import org.bartram.vidtag.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
    "vidtag.raindrop.fallback-collection=TestCollection",
    "vidtag.raindrop.collection-cache-ttl=48h",
    "vidtag.raindrop.collections-list-cache-ttl=2h"
})
class RaindropPropertiesTest {

    @Autowired
    private RaindropProperties raindropProperties;

    @Test
    void shouldBindFallbackCollection() {
        assertThat(raindropProperties.getFallbackCollection()).isEqualTo("TestCollection");
    }

    @Test
    void shouldBindCollectionCacheTtl() {
        assertThat(raindropProperties.getCollectionCacheTtl()).isEqualTo(Duration.ofHours(48));
    }

    @Test
    void shouldBindCollectionsListCacheTtl() {
        assertThat(raindropProperties.getCollectionsListCacheTtl()).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void shouldUseDefaultValues() {
        // Test with default application.yaml values
        assertThat(raindropProperties.getFallbackCollection()).isNotNull();
        assertThat(raindropProperties.getCollectionCacheTtl()).isNotNull();
        assertThat(raindropProperties.getCollectionsListCacheTtl()).isNotNull();
    }
}
