package com.medha.catalogservice.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.medha.catalogservice.dto.CacheStatsResponse;
import com.medha.catalogservice.exception.InvalidCacheNameException;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Exposes the native Caffeine {@link CacheStats} for every registered cache, and
 * offers manual clear operations for demo/admin purposes. This is intentionally
 * separate from Micrometer/Actuator metrics: it talks to
 * {@code com.github.benmanes.caffeine.cache.Cache} directly so learners can see
 * exactly what Caffeine itself tracks (hit/miss/eviction/load counters).
 */
@Service
public class CacheAdminService {

    private final CacheManager cacheManager;

    public CacheAdminService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public List<CacheStatsResponse> getAllCacheStats() {
        return cacheManager.getCacheNames().stream()
                .map(this::getCacheStats)
                .toList();
    }

    public CacheStatsResponse getCacheStats(String cacheName) {
        Cache<Object, Object> nativeCache = nativeCache(cacheName);
        CacheStats stats = nativeCache.stats();

        return new CacheStatsResponse(
                cacheName,
                nativeCache.estimatedSize(),
                stats.requestCount(),
                stats.hitCount(),
                stats.missCount(),
                stats.hitRate(),
                stats.missRate(),
                stats.evictionCount(),
                stats.evictionWeight(),
                stats.loadSuccessCount(),
                stats.loadFailureCount(),
                stats.averageLoadPenalty() / TimeUnit.MILLISECONDS.toNanos(1));
    }

    public void clearCache(String cacheName) {
        nativeCache(cacheName).invalidateAll();
    }

    public void clearAllCaches() {
        cacheManager.getCacheNames().forEach(this::clearCache);
    }

    private Cache<Object, Object> nativeCache(String cacheName) {
        org.springframework.cache.Cache springCache = cacheManager.getCache(cacheName);
        if (!(springCache instanceof CaffeineCache caffeineCache)) {
            throw new InvalidCacheNameException(cacheName);
        }
        return caffeineCache.getNativeCache();
    }
}
