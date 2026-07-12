package com.campus.trend.campus_pulse.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SupporterCreatorInsightsResp(
        int days,
        LocalDate fromDate,
        LocalDate toDate,
        LocalDateTime generatedAt,
        SupporterCreatorInsightSummaryResp summary,
        List<SupporterCreatorInsightDailyResp> trend) {
}
