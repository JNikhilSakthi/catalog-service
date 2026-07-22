package com.medha.catalogservice.controller;

import com.medha.catalogservice.dto.CacheStatsResponse;
import com.medha.catalogservice.service.CacheAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only + admin surface over the raw Caffeine cache statistics. This is the
 * "look inside the cache" endpoint for the demo: call a product endpoint a few
 * times, then hit {@code GET /api/v1/cache/stats} to see hit rate and eviction
 * counts move.
 */
@RestController
@RequestMapping("/api/v1/cache")
public class CacheAdminController {

    private final CacheAdminService cacheAdminService;

    public CacheAdminController(CacheAdminService cacheAdminService) {
        this.cacheAdminService = cacheAdminService;
    }

    @GetMapping("/stats")
    public ResponseEntity<List<CacheStatsResponse>> getAllStats() {
        return ResponseEntity.ok(cacheAdminService.getAllCacheStats());
    }

    @GetMapping("/stats/{cacheName}")
    public ResponseEntity<CacheStatsResponse> getStats(@PathVariable String cacheName) {
        return ResponseEntity.ok(cacheAdminService.getCacheStats(cacheName));
    }

    @DeleteMapping("/{cacheName}")
    public ResponseEntity<Void> clearCache(@PathVariable String cacheName) {
        cacheAdminService.clearCache(cacheName);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearAllCaches() {
        cacheAdminService.clearAllCaches();
        return ResponseEntity.noContent().build();
    }
}
