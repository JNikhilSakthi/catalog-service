package com.medha.catalogservice.dto;

import org.springframework.data.domain.Page;

import java.io.Serializable;
import java.util.List;

/**
 * Simple, cache-friendly page wrapper. Spring Data's {@link Page} implementations are
 * not guaranteed to be efficiently (de)serializable/cacheable across all scenarios, so
 * service methods that are {@code @Cacheable} return this plain, {@link Serializable}
 * record instead of a {@code Page<T>} directly.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) implements Serializable {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
