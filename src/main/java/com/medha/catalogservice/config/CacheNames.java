package com.medha.catalogservice.config;

/**
 * Central registry of the named Caffeine caches used across the application.
 * Keeping the names as constants avoids typos between {@code @Cacheable} /
 * {@code @CacheEvict} annotations and the cache registration in {@link CacheConfig}.
 */
public final class CacheNames {

    /** Individual product lookups, keyed by product id. Read-heavy, small entries. */
    public static final String PRODUCTS = "products";

    /** Paged product listings (by category / active flag). Larger entries, invalidated often. */
    public static final String PRODUCT_LISTS = "productLists";

    /** Free-text product search results, keyed by normalized query + page. */
    public static final String PRODUCT_SEARCH = "productSearch";

    /** Individual category lookups, keyed by category id. Rarely changes, long TTL. */
    public static final String CATEGORIES = "categories";

    private CacheNames() {
    }
}
