package com.medha.catalogservice.dto;

import jakarta.validation.constraints.NotNull;

public record StockAdjustmentRequest(

        @NotNull(message = "delta is required")
        Integer delta
) {
}
