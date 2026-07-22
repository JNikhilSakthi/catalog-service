package com.medha.catalogservice.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        Integer stockQuantity,
        boolean active,
        Long categoryId,
        String categoryName,
        Instant createdAt,
        Instant updatedAt
) {
}
