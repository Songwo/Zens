package com.campus.trend.campus_pulse.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record SupporterStatusResp(
        boolean active,
        String planCode,
        String planName,
        LocalDateTime startsAt,
        LocalDateTime expiresAt,
        long remainingDays,
        List<String> benefits,
        List<String> capabilities) {
}
