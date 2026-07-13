package com.campus.trend.campus_pulse.dto.response;

import java.time.LocalDateTime;

public record SupporterVoucherGrantResp(
        Long id,
        String sourceOrderNo,
        String planCode,
        Integer quota,
        String status,
        String code,
        String redemptionUrl,
        LocalDateTime grantedAt,
        LocalDateTime issuedAt
) {
}
