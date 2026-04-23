package com.flashsale.dto;

import java.time.LocalDateTime;

public record JoinSaleResponse(
        String orderId,
        LocalDateTime expiresAt,
        String paymentUrl,
        String error,
        Integer waitlistPosition,
        Integer retryAfter
) {

    public static JoinSaleResponse success(String orderId, LocalDateTime expiresAt, String paymentUrl) {
        return new JoinSaleResponse(orderId, expiresAt, paymentUrl, null, null, null);
    }

    public static JoinSaleResponse soldOut(int waitlistPosition) {
        return new JoinSaleResponse(null, null, null, "SOLD_OUT", waitlistPosition, null);
    }

    public static JoinSaleResponse rateLimited(int retryAfter) {
        return new JoinSaleResponse(null, null, null, "TOO_MANY_REQUESTS", null, retryAfter);
    }

    public static JoinSaleResponse blocked(String error) {
        return new JoinSaleResponse(null, null, null, error, null, null);
    }
}
