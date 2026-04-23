package com.flashsale.dto;

import java.time.LocalDateTime;

public record EnqueuedOrderMessage(
        String orderId,
        Long saleId,
        Long userId,
        Integer quantity,
        LocalDateTime reservedAt,
        LocalDateTime expiresAt
) {
}
