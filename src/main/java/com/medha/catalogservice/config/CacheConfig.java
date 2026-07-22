package com.medha.catalogservice.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires up a {@link CaffeineCacheManager} with one distinctly-tuned {@link Caffeine}
 * builder per named cache. Spring's {@code CaffeineCacheManager} normally applies a
 * single {@code Caffeine} spec to every cache it manages; here each cache is
 * registered individually via {@link CaffeineCacheManager#registerCustomCache} so
 * that, e.g., "products" can have a longer TTL than "productSearch".
 *
 * <p>{@code recordStats()} is enabled on every cache so that hit/miss/eviction
 * counters are available at runtime through {@code CacheAdminController}.</p>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(CacheProperties properties) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        cacheManager.registerCustomCache(CacheNames.PRODUCTS, buildCaffeine(properties.getProducts()).build());
        cacheManager.registerCustomCache(CacheNames.PRODUCT_LISTS, buildCaffeine(properties.getProductLists()).build());
        cacheManager.registerCustomCache(CacheNames.PRODUCT_SEARCH, buildCaffeine(properties.getProductSearch()).build());
        cacheManager.registerCustomCache(CacheNames.CATEGORIES, buildCaffeine(properties.getCategories()).build());

        return cacheManager;
    }

    private Caffeine<Object, Object> buildCaffeine(CacheProperties.Spec spec) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .maximumSize(spec.getMaximumSize())
                .recordStats();

        if (spec.getExpireAfterWrite() != null) {
            builder.expireAfterWrite(spec.getExpireAfterWrite());
        }
        if (spec.getExpireAfterAccess() != null) {
            builder.expireAfterAccess(spec.getExpireAfterAccess());
        }
        return builder;
    }
}
