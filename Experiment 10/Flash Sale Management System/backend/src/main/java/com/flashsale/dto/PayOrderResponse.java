package com.flashsale.dto;

import java.time.LocalDateTime;

public record PayOrderResponse(
        String orderId,
        String status,
        LocalDateTime paidAt
) {
}
