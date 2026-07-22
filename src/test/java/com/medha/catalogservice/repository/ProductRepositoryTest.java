package com.medha.catalogservice.repository;

import com.medha.catalogservice.domain.Category;
import com.medha.catalogservice.domain.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Category electronics;

    @BeforeEach
    void setUp() {
        electronics = categoryRepository.save(Category.builder()
                .name("Electronics")
                .description("Gadgets")
                .build());

        productRepository.save(Product.builder()
                .sku("ELEC-0001")
                .name("Wireless Headphones")
                .description("Noise cancelling")
                .price(new BigDecimal("99.99"))
                .stockQuantity(10)
                .active(true)
                .category(electronics)
                .build());

        productRepository.save(Product.builder()
                .sku("ELEC-0002")
                .name("Discontinued Speaker")
                .description("Old model")
                .price(new BigDecimal("9.99"))
                .stockQuantity(0)
                .active(false)
                .category(electronics)
                .build());
    }

    @Test
    void findBySku_returnsProduct_whenExists() {
        assertThat(productRepository.findBySku("ELEC-0001")).isPresent();
        assertThat(productRepository.findBySku("UNKNOWN")).isEmpty();
    }

    @Test
    void existsBySku_reflectsPersistedState() {
        assertThat(productRepository.existsBySku("ELEC-0001")).isTrue();
        assertThat(productRepository.existsBySku("NOPE")).isFalse();
    }

    @Test
    void findByCategoryIdAndActive_filtersCorrectly() {
        var page = productRepository.findByCategoryIdAndActive(electronics.getId(), true, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getSku()).isEqualTo("ELEC-0001");
    }

    @Test
    void findByNameContainingIgnoreCase_isCaseInsensitive() {
        var page = productRepository.findByNameContainingIgnoreCase("headphones", PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getSku()).isEqualTo("ELEC-0001");
    }
}
