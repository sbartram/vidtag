package org.bartram.vidtag.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Cache configuration for Redis.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private final RaindropProperties raindropProperties;

    public CacheConfig(RaindropProperties raindropProperties) {
        this.raindropProperties = raindropProperties;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        cacheConfigurations.put(
                "playlist-collections",
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(raindropProperties.getCollectionCacheTtl()));

        cacheConfigurations.put(
                "raindrop-collections-list",
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(raindropProperties.getCollectionsListCacheTtl()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig())
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
