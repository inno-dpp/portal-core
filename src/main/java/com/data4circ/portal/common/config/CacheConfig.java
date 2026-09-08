package com.data4circ.portal.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache configuration for the application
 * Enables Spring Cache Abstraction with Caffeine as the cache provider
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Configure Caffeine cache manager
     * Cache name: "datasetCount" for CKAN dataset count caching
     * TTL: 1 minute
     * Max size: 100 entries
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("datasetCount");
        cacheManager.setCaffeine(caffeineCacheBuilder());
        return cacheManager;
    }

    /**
     * Build Caffeine cache with specific configuration
     * - expireAfterWrite: Cache entries expire 1 minute after being written
     * - maximumSize: Maximum of 100 entries in cache
     * - recordStats: Enable statistics tracking for monitoring
     */
    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .maximumSize(100)
                .recordStats();
    }
}