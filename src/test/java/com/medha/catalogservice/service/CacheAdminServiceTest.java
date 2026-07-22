package com.medha.catalogservice.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.medha.catalogservice.dto.CacheStatsResponse;
import com.medha.catalogservice.exception.InvalidCacheNameException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheAdminServiceTest {

    private CaffeineCacheManager cacheManager;
    private CacheAdminService cacheAdminService;

    private static final String CACHE_NAME = "demoCache";

    @BeforeEach
    void setUp() {
        cacheManager = new CaffeineCacheManager();
        cacheManager.registerCustomCache(CACHE_NAME, Caffeine.newBuilder().maximumSize(10).recordStats().build());
        cacheAdminService = new CacheAdminService(cacheManager);
    }

    @Test
    void getCacheStats_reflectsHitsAndMisses() {
        org.springframework.cache.Cache springCache = cacheManager.getCache(CACHE_NAME);

        springCache.get("key-1"); // miss
        springCache.put("key-1", "value-1");
        springCache.get("key-1"); // hit
        springCache.get("key-1"); // hit

        CacheStatsResponse stats = cacheAdminService.getCacheStats(CACHE_NAME);

        assertThat(stats.cacheName()).isEqualTo(CACHE_NAME);
        assertThat(stats.hitCount()).isEqualTo(2);
        assertThat(stats.missCount()).isEqualTo(1);
        assertThat(stats.requestCount()).isEqualTo(3);
    }

    @Test
    void clearCache_removesAllEntries() {
        org.springframework.cache.Cache springCache = cacheManager.getCache(CACHE_NAME);
        springCache.put("key-1", "value-1");

        cacheAdminService.clearCache(CACHE_NAME);

        assertThat(springCache.get("key-1")).isNull();
    }

    @Test
    void getCacheStats_throwsInvalidCacheName_whenUnknown() {
        assertThatThrownBy(() -> cacheAdminService.getCacheStats("unknown"))
                .isInstanceOf(InvalidCacheNameException.class);
    }
}
