package com.campus.trend.campus_pulse.dto.response;

public record SupporterVoucherInventoryResp(
        int quota,
        long available,
        long assigned,
        long pendingGrants
) {
}
