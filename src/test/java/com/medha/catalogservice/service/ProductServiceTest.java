package com.medha.catalogservice.service;

import com.medha.catalogservice.config.CacheProperties;
import com.medha.catalogservice.domain.Category;
import com.medha.catalogservice.domain.Product;
import com.medha.catalogservice.dto.ProductRequest;
import com.medha.catalogservice.dto.ProductResponse;
import com.medha.catalogservice.dto.StockAdjustmentRequest;
import com.medha.catalogservice.exception.DuplicateResourceException;
import com.medha.catalogservice.exception.ResourceNotFoundException;
import com.medha.catalogservice.mapper.ProductMapper;
import com.medha.catalogservice.repository.CategoryRepository;
import com.medha.catalogservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link ProductService}'s business logic, run without a Spring
 * context. Because the {@code @Cacheable}/{@code @CacheEvict} annotations are only
 * honored through a Spring-managed proxy, caching behavior itself is NOT exercised
 * here -- see {@code ProductCacheIntegrationTest} for that. This class only verifies
 * the underlying logic is correct regardless of caching.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private final ProductMapper productMapper = new ProductMapper();

    private ProductService productService;

    private Category category;
    private Product product;

    @BeforeEach
    void setUp() {
        CacheProperties cacheProperties = new CacheProperties();
        cacheProperties.setSimulatedRepositoryLatencyMs(0);
        productService = new ProductService(productRepository, categoryRepository, productMapper, cacheProperties);

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
    }

    @Test
    void getProductById_returnsMappedResponse_whenFound() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        ProductResponse response = productService.getProductById(10L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.sku()).isEqualTo("ELEC-0001");
        assertThat(response.categoryName()).isEqualTo("Electronics");
    }

    @Test
    void getProductById_throwsNotFound_whenMissing() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void createProduct_savesAndReturnsResponse() {
        ProductRequest request = new ProductRequest("ELEC-0002", "Keyboard", "Mechanical",
                new BigDecimal("59.99"), 20, true, 1L);

        when(productRepository.existsBySku("ELEC-0002")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });

        ProductResponse response = productService.createProduct(request);

        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.sku()).isEqualTo("ELEC-0002");
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void createProduct_throwsDuplicate_whenSkuAlreadyExists() {
        ProductRequest request = new ProductRequest("ELEC-0001", "Dup", "Dup",
                new BigDecimal("10.00"), 5, true, 1L);

        when(productRepository.existsBySku("ELEC-0001")).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void adjustStock_increasesQuantity() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.adjustStock(10L, new StockAdjustmentRequest(5));

        assertThat(response.stockQuantity()).isEqualTo(55);
    }

    @Test
    void adjustStock_throwsIllegalArgument_whenResultWouldBeNegative() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.adjustStock(10L, new StockAdjustmentRequest(-1000)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteProduct_removesEntity_whenFound() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        productService.deleteProduct(10L);

        verify(productRepository).delete(product);
    }

    @Test
    void deleteProduct_throwsNotFound_whenMissing() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProduct(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
