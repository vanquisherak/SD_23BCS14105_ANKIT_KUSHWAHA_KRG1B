package com.flashsale.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PurchaseRequest(
        @NotNull Long userId,
        @NotNull @Min(1) Integer quantity
) {
}
