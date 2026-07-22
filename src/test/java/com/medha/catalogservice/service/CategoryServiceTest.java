package com.medha.catalogservice.service;

import com.medha.catalogservice.domain.Category;
import com.medha.catalogservice.dto.CategoryRequest;
import com.medha.catalogservice.dto.CategoryResponse;
import com.medha.catalogservice.exception.DuplicateResourceException;
import com.medha.catalogservice.exception.ResourceNotFoundException;
import com.medha.catalogservice.mapper.CategoryMapper;
import com.medha.catalogservice.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    private final CategoryMapper categoryMapper = new CategoryMapper();

    private CategoryService categoryService;

    private Category category;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, categoryMapper);
        category = Category.builder().id(1L).name("Electronics").description("Gadgets").build();
    }

    @Test
    void getCategoryById_returnsMappedResponse_whenFound() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        CategoryResponse response = categoryService.getCategoryById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Electronics");
    }

    @Test
    void getCategoryById_throwsNotFound_whenMissing() {
        when(categoryRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryById(2L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createCategory_savesAndReturnsResponse() {
        CategoryRequest request = new CategoryRequest("Books", "All books");
        when(categoryRepository.existsByName("Books")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        CategoryResponse response = categoryService.createCategory(request);

        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.name()).isEqualTo("Books");
    }

    @Test
    void createCategory_throwsDuplicate_whenNameAlreadyExists() {
        CategoryRequest request = new CategoryRequest("Electronics", "dup");
        when(categoryRepository.existsByName("Electronics")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void deleteCategory_removesEntity_whenFound() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        categoryService.deleteCategory(1L);

        verify(categoryRepository).delete(category);
    }
}
