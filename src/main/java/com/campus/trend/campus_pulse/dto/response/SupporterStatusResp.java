package com.campus.trend.campus_pulse.dto.response;

import java.time.LocalDateTime;

public record SupporterStatusResp(
        boolean active,
        String planCode,
        String planName,
        LocalDateTime startsAt,
        LocalDateTime expiresAt) {
}
