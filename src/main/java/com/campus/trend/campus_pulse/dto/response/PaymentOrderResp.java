package com.campus.trend.campus_pulse.dto.response;

import java.time.LocalDateTime;

public record PaymentOrderResp(
        String orderNo,
        String planCode,
        String planName,
        int amountCents,
        String currency,
        String provider,
        String status,
        String checkoutUrl,
        LocalDateTime paidAt,
        LocalDateTime expiresAt,
        LocalDateTime createdAt) {
}
