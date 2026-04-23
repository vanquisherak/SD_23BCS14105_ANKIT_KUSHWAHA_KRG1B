package com.flashsale.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateSaleEventRequest(
        @NotNull Long productId,
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime,
        @NotNull @Min(0) Integer saleStock,
        @NotNull @Min(1) Integer maxPerUser,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal salePrice
) {
}
