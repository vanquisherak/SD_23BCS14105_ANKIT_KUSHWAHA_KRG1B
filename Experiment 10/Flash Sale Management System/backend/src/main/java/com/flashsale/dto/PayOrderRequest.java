package com.flashsale.dto;

import jakarta.validation.constraints.NotNull;

public record PayOrderRequest(
        @NotNull Long userId,
        String paymentReference
) {
}
