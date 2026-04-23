package com.flashsale.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SaleEventSummaryResponse(
        Long saleEventId,
        Long productId,
        String productName,
        String productSku,
        BigDecimal regularPrice,
        BigDecimal salePrice,
        Integer saleStock,
        Integer maxPerUser,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status
) {
}
