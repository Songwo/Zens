package com.campus.trend.campus_pulse.dto.response;

import java.util.List;

public record SupporterPlanResp(
        String code,
        String name,
        String description,
        int priceCents,
        String currency,
        int durationDays,
        List<String> benefits,
        boolean paymentAvailable) {
}
