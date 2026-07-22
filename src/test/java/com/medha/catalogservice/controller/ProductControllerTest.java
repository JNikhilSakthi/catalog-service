package com.medha.catalogservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medha.catalogservice.dto.PageResponse;
import com.medha.catalogservice.dto.ProductRequest;
import com.medha.catalogservice.dto.ProductResponse;
import com.medha.catalogservice.exception.ResourceNotFoundException;
import com.medha.catalogservice.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    private ProductResponse sampleProduct() {
        return new ProductResponse(10L, "ELEC-0001", "Headphones", "Wireless",
                new BigDecimal("99.99"), 50, true, 1L, "Electronics", Instant.now(), Instant.now());
    }

    @Test
    void getProduct_returnsOk_withResponseTimeHeader() throws Exception {
        when(productService.getProductById(10L)).thenReturn(sampleProduct());

        mockMvc.perform(get("/api/v1/products/{id}", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("ELEC-0001"))
                .andExpect(header().exists("X-Response-Time-Ms"));
    }

    @Test
    void getProduct_returns404_whenNotFound() throws Exception {
        when(productService.getProductById(99L)).thenThrow(ResourceNotFoundException.forEntity("Product", 99L));

        mockMvc.perform(get("/api/v1/products/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void listProducts_returnsPage() throws Exception {
        PageResponse<ProductResponse> page = new PageResponse<>(List.of(sampleProduct()), 0, 20, 1, 1);
        when(productService.listProducts(any(), any(), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("ELEC-0001"));
    }

    @Test
    void createProduct_returns400_whenValidationFails() throws Exception {
        ProductRequest invalid = new ProductRequest("", "", null, null, null, false, null);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());
    }

    @Test
    void createProduct_returns201_whenValid() throws Exception {
        ProductRequest request = new ProductRequest("ELEC-0002", "Keyboard", "Mechanical",
                new BigDecimal("59.99"), 20, true, 1L);
        when(productService.createProduct(any(ProductRequest.class))).thenReturn(sampleProduct());

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void deleteProduct_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/products/{id}", 10L))
                .andExpect(status().isNoContent());
    }
}
