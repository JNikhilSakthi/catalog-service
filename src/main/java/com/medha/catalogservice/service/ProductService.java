package com.medha.catalogservice.service;

import com.medha.catalogservice.config.CacheNames;
import com.medha.catalogservice.config.CacheProperties;
import com.medha.catalogservice.domain.Category;
import com.medha.catalogservice.domain.Product;
import com.medha.catalogservice.dto.PageResponse;
import com.medha.catalogservice.dto.ProductRequest;
import com.medha.catalogservice.dto.ProductResponse;
import com.medha.catalogservice.dto.StockAdjustmentRequest;
import com.medha.catalogservice.exception.DuplicateResourceException;
import com.medha.catalogservice.exception.ResourceNotFoundException;
import com.medha.catalogservice.mapper.ProductMapper;
import com.medha.catalogservice.repository.CategoryRepository;
import com.medha.catalogservice.repository.ProductRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the product catalog read/write paths. Every read path that is expensive
 * enough to matter is fronted by a Caffeine cache (registered in {@code CacheConfig})
 * through Spring's {@code @Cacheable}/{@code @CachePut}/{@code @CacheEvict}
 * annotations; a small artificial delay ({@link CacheProperties#getSimulatedRepositoryLatencyMs()})
 * stands in for a "slow" datastore so that the speedup from a cache hit is visible
 * end-to-end (see {@code ProductController} response timing header).
 */
@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final CacheProperties cacheProperties;

    public ProductService(ProductRepository productRepository,
                           CategoryRepository categoryRepository,
                           ProductMapper productMapper,
                           CacheProperties cacheProperties) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productMapper = productMapper;
        this.cacheProperties = cacheProperties;
    }

    /**
     * Cached by product id in the "products" cache. This is the highest-traffic,
     * highest-value cache in the demo: product detail pages are read far more often
     * than products are written.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.PRODUCTS, key = "#id")
    public ProductResponse getProductById(Long id) {
        simulateRepositoryLatency();
        return productMapper.toResponse(findProductOrThrow(id));
    }

    /**
     * Cached in "productLists", keyed by the full parameter combination so that
     * different category/active/page/size requests don't collide.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.PRODUCT_LISTS,
            key = "#categoryId + '_' + #activeOnly + '_' + #page + '_' + #size")
    public PageResponse<ProductResponse> listProducts(Long categoryId, Boolean activeOnly, int page, int size) {
        simulateRepositoryLatency();
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").ascending());

        Page<Product> result;
        if (categoryId != null && activeOnly != null) {
            result = productRepository.findByCategoryIdAndActive(categoryId, activeOnly, pageable);
        } else if (categoryId != null) {
            result = productRepository.findByCategoryId(categoryId, pageable);
        } else if (activeOnly != null) {
            result = productRepository.findByActive(activeOnly, pageable);
        } else {
            result = productRepository.findAll(pageable);
        }

        return PageResponse.from(result.map(productMapper::toResponse));
    }

    /**
     * Cached in "productSearch", keyed by the normalized query + page/size. Has the
     * shortest TTL of the four caches since search relevance is more sensitive to
     * stock/catalog churn than a single product lookup.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.PRODUCT_SEARCH, key = "#query.toLowerCase() + '_' + #page + '_' + #size")
    public PageResponse<ProductResponse> searchProducts(String query, int page, int size) {
        simulateRepositoryLatency();
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<Product> result = productRepository.findByNameContainingIgnoreCase(query, pageable);
        return PageResponse.from(result.map(productMapper::toResponse));
    }

    /**
     * Writes never populate a cache themselves; they only evict. This keeps the
     * "who is allowed to put things in the cache" rule simple: only the read paths do.
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PRODUCT_LISTS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PRODUCT_SEARCH, allEntries = true)
    })
    public ProductResponse createProduct(ProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateResourceException("Product already exists with sku: " + request.sku());
        }
        Category category = findCategoryOrThrow(request.categoryId());
        Product saved = productRepository.save(productMapper.toEntity(request, category));
        return productMapper.toResponse(saved);
    }

    /**
     * {@code @CachePut} refreshes the "products" entry for this id in place (so a
     * subsequent {@link #getProductById} is a hit against the new value) while the
     * list/search caches are evicted wholesale because we don't know which pages the
     * updated product now belongs on.
     */
    @Caching(
            put = {
                    @CachePut(cacheNames = CacheNames.PRODUCTS, key = "#id")
            },
            evict = {
                    @CacheEvict(cacheNames = CacheNames.PRODUCT_LISTS, allEntries = true),
                    @CacheEvict(cacheNames = CacheNames.PRODUCT_SEARCH, allEntries = true)
            }
    )
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = findProductOrThrow(id);

        productRepository.findBySku(request.sku()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new DuplicateResourceException("Product already exists with sku: " + request.sku());
            }
        });

        Category category = findCategoryOrThrow(request.categoryId());
        productMapper.updateEntity(product, request, category);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Caching(
            put = {
                    @CachePut(cacheNames = CacheNames.PRODUCTS, key = "#id")
            },
            evict = {
                    @CacheEvict(cacheNames = CacheNames.PRODUCT_LISTS, allEntries = true),
                    @CacheEvict(cacheNames = CacheNames.PRODUCT_SEARCH, allEntries = true)
            }
    )
    public ProductResponse adjustStock(Long id, StockAdjustmentRequest request) {
        Product product = findProductOrThrow(id);
        int newQuantity = product.getStockQuantity() + request.delta();
        if (newQuantity < 0) {
            throw new IllegalArgumentException("Stock adjustment would result in negative quantity for product " + id);
        }
        product.setStockQuantity(newQuantity);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.PRODUCT_LISTS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PRODUCT_SEARCH, allEntries = true)
    })
    public void deleteProduct(Long id) {
        Product product = findProductOrThrow(id);
        productRepository.delete(product);
    }

    private Product findProductOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("Product", id));
    }

    private Category findCategoryOrThrow(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("Category", categoryId));
    }

    private void simulateRepositoryLatency() {
        long latencyMs = cacheProperties.getSimulatedRepositoryLatencyMs();
        if (latencyMs <= 0) {
            return;
        }
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
