package com.medha.catalogservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Product Catalog Cache demo application.
 *
 * <p>This project isolates exactly one technology for teaching purposes: the
 * <a href="https://github.com/ben-manes/caffeine">Caffeine</a> in-process caching
 * library, wired in through Spring's {@code @EnableCaching} abstraction. Every other
 * concern (JPA, validation, REST) exists only to give the cache something realistic
 * to sit in front of.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }
}
