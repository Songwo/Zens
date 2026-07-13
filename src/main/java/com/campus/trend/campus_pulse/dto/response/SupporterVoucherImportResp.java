package com.campus.trend.campus_pulse.dto.response;

public record SupporterVoucherImportResp(
        int quota,
        int submitted,
        int imported,
        int duplicates,
        int pendingIssued
) {
}
