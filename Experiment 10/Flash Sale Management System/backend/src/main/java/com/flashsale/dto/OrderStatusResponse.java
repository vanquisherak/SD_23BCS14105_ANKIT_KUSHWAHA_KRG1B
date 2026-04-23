package com.flashsale.dto;

import com.flashsale.entity.OrderStatus;

import java.time.LocalDateTime;

public record OrderStatusResponse(
        String orderId,
        Long saleId,
        Long userId,
        Integer quantity,
        OrderStatus status,
        LocalDateTime reservedAt,
        LocalDateTime expiresAt,
        LocalDateTime paidAt,
        String paymentUrl
) {
}
