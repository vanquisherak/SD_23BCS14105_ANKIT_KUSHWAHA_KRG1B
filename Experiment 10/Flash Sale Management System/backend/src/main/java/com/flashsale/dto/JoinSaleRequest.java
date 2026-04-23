package com.flashsale.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record JoinSaleRequest(
        @NotNull Long userId,
        @NotNull @Min(1) Integer quantity,
        String idempotencyKey
) {
}
