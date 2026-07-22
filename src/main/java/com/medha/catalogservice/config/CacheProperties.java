package com.medha.catalogservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Externalized tuning knobs for every Caffeine cache in the application, plus a
 * simulated repository latency used to make the caching effect observable in
 * response times without needing a real slow datastore.
 *
 * <p>Bound from the {@code catalog.cache.*} keys in application.yml.</p>
 */
@ConfigurationProperties(prefix = "catalog.cache")
@Validated
public class CacheProperties {

    @NotNull
    @Valid
    private Spec products = new Spec(500, Duration.ofMinutes(10), null);

    @NotNull
    @Valid
    private Spec productLists = new Spec(200, Duration.ofMinutes(5), null);

    @NotNull
    @Valid
    private Spec productSearch = new Spec(100, Duration.ofMinutes(2), null);

    @NotNull
    @Valid
    private Spec categories = new Spec(100, null, Duration.ofMinutes(30));

    /** Artificial delay (ms) added before hitting the repository, to make cache hits visibly faster. */
    @Min(0)
    private long simulatedRepositoryLatencyMs = 250;

    public Spec getProducts() {
        return products;
    }

    public void setProducts(Spec products) {
        this.products = products;
    }

    public Spec getProductLists() {
        return productLists;
    }

    public void setProductLists(Spec productLists) {
        this.productLists = productLists;
    }

    public Spec getProductSearch() {
        return productSearch;
    }

    public void setProductSearch(Spec productSearch) {
        this.productSearch = productSearch;
    }

    public Spec getCategories() {
        return categories;
    }

    public void setCategories(Spec categories) {
        this.categories = categories;
    }

    public long getSimulatedRepositoryLatencyMs() {
        return simulatedRepositoryLatencyMs;
    }

    public void setSimulatedRepositoryLatencyMs(long simulatedRepositoryLatencyMs) {
        this.simulatedRepositoryLatencyMs = simulatedRepositoryLatencyMs;
    }

    /**
     * Per-cache sizing/expiry configuration. Exactly one of {@code expireAfterWrite}
     * or {@code expireAfterAccess} is expected to be set for a given cache.
     */
    public static class Spec {

        @Min(1)
        private long maximumSize;

        private Duration expireAfterWrite;

        private Duration expireAfterAccess;

        public Spec() {
        }

        public Spec(long maximumSize, Duration expireAfterWrite, Duration expireAfterAccess) {
            this.maximumSize = maximumSize;
            this.expireAfterWrite = expireAfterWrite;
            this.expireAfterAccess = expireAfterAccess;
        }

        public long getMaximumSize() {
            return maximumSize;
        }

        public void setMaximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
        }

        public Duration getExpireAfterWrite() {
            return expireAfterWrite;
        }

        public void setExpireAfterWrite(Duration expireAfterWrite) {
            this.expireAfterWrite = expireAfterWrite;
        }

        public Duration getExpireAfterAccess() {
            return expireAfterAccess;
        }

        public void setExpireAfterAccess(Duration expireAfterAccess) {
            this.expireAfterAccess = expireAfterAccess;
        }
    }
}
