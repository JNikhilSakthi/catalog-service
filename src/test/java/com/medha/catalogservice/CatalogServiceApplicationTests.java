package com.medha.catalogservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CatalogServiceApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the full application context -- including CacheConfig's Caffeine
        // cache registrations -- wires up without errors.
    }
}
