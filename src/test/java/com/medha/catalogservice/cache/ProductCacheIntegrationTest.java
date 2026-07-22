package com.medha.catalogservice.cache;

import com.medha.catalogservice.config.CacheNames;
import com.medha.catalogservice.domain.Category;
import com.medha.catalogservice.domain.Product;
import com.medha.catalogservice.dto.ProductRequest;
import com.medha.catalogservice.dto.ProductResponse;
import com.medha.catalogservice.repository.CategoryRepository;
import com.medha.catalogservice.repository.ProductRepository;
import com.medha.catalogservice.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves that the Caffeine-backed Spring cache abstraction is actually intercepting
 * calls: the full application context is loaded (so the {@code @Cacheable} proxy is
 * active) with {@link ProductRepository} mocked out, so any call that hits the
 * database twice for the same key is a caching bug, not a coincidence of test data.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProductCacheIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private ProductRepository productRepository;

    @MockBean
    private CategoryRepository categoryRepository;

    private Product product;
    private Category category;

    @BeforeEach
    void setUp() {
        category = Category.builder().id(1L).name("Electronics").description("Gadgets").build();
        product = Product.builder()
                .id(10L)
                .sku("ELEC-0001")
                .name("Headphones")
                .description("Wireless")
                .price(new BigDecimal("99.99"))
                .stockQuantity(50)
                .active(true)
                .category(category)
                .build();

        Objects.requireNonNull(cacheManager.getCache(CacheNames.PRODUCTS)).clear();
        Objects.requireNonNull(cacheManager.getCache(CacheNames.PRODUCT_LISTS)).clear();
        Objects.requireNonNull(cacheManager.getCache(CacheNames.PRODUCT_SEARCH)).clear();
    }

    @Test
    void getProductById_hitsRepositoryOnlyOnce_forRepeatedCalls() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        ProductResponse first = productService.getProductById(10L);
        ProductResponse second = productService.getProductById(10L);
        ProductResponse third = productService.getProductById(10L);

        assertThat(first).isEqualTo(second).isEqualTo(third);
        verify(productRepository, times(1)).findById(10L);
    }

    @Test
    void updateProduct_evictsAndRefreshesProductsCache() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.findBySku("ELEC-0001")).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Call 1: primes the "products" cache entry for id 10.
        productService.getProductById(10L);
        verify(productRepository, times(1)).findById(10L);

        // Call 2: updateProduct looks the entity up directly (uncached) before saving,
        // then @CachePut refreshes the "products" cache entry with the new value.
        ProductRequest updateRequest = new ProductRequest("ELEC-0001", "Updated Headphones", "Wireless v2",
                new BigDecimal("109.99"), 40, true, 1L);
        ProductResponse updated = productService.updateProduct(10L, updateRequest);
        assertThat(updated.name()).isEqualTo("Updated Headphones");
        verify(productRepository, times(2)).findById(10L);

        // A subsequent get must be served from the refreshed cache entry -- no 3rd call.
        ProductResponse afterUpdate = productService.getProductById(10L);
        assertThat(afterUpdate.name()).isEqualTo("Updated Headphones");
        verify(productRepository, times(2)).findById(10L);
    }

    @Test
    void deleteProduct_evictsProductsCacheEntry() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        // Call 1: primes the "products" cache entry for id 10.
        productService.getProductById(10L);
        verify(productRepository, times(1)).findById(10L);

        // Call 2: deleteProduct looks the entity up directly (uncached) before removing it,
        // and evicts the "products" cache entry as a side effect.
        productService.deleteProduct(10L);
        verify(productRepository, times(2)).findById(10L);

        when(productRepository.findById(10L)).thenReturn(Optional.empty());
        org.junit.jupiter.api.Assertions.assertThrows(
                com.medha.catalogservice.exception.ResourceNotFoundException.class,
                () -> productService.getProductById(10L));

        // Call 3: cache was evicted by the delete, so this must go back to the repository.
        verify(productRepository, times(3)).findById(10L);
    }

    @Test
    void listProducts_isCached_forIdenticalParameters() {
        when(productRepository.findAll(org.mockito.ArgumentMatchers.<org.springframework.data.domain.Pageable>any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        productService.listProducts(null, null, 0, 20);
        productService.listProducts(null, null, 0, 20);

        verify(productRepository, times(1))
                .findAll(org.mockito.ArgumentMatchers.<org.springframework.data.domain.Pageable>any());
    }
}
