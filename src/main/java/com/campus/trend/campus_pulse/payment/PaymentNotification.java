package com.campus.trend.campus_pulse.payment;

import java.time.LocalDateTime;

public record PaymentNotification(
        String eventId,
        String orderNo,
        String providerOrderNo,
        String status,
        int amountCents,
        String currency,
        LocalDateTime paidAt) {
}
