package com.medha.catalogservice.dto;

/**
 * Snapshot of a single named Caffeine cache's runtime statistics, sourced directly
 * from {@code com.github.benmanes.caffeine.cache.stats.CacheStats} rather than a
 * generic metrics abstraction, so the raw Caffeine numbers are visible as-is.
 */
public record CacheStatsResponse(
        String cacheName,
        long estimatedSize,
        long requestCount,
        long hitCount,
        long missCount,
        double hitRate,
        double missRate,
        long evictionCount,
        long evictionWeight,
        long loadSuccessCount,
        long loadFailureCount,
        double averageLoadPenaltyMillis
) {
}
