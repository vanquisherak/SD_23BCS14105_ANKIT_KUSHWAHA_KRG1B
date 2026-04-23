package com.flashsale.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PurchaseResponse(
        Long orderId,
        Long saleEventId,
        Long userId,
        Integer quantity,
        BigDecimal totalAmount,
        LocalDateTime purchasedAt
) {
}
