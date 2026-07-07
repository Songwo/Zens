package com.campus.trend.campus_pulse.dto.response;

import lombok.Builder;
import lombok.Data;

/** 积分概览:当前余额 + 本月收支合计。 */
@Data
@Builder
public class PointSummaryResp {
    private int points;
    private int monthEarned;
    private int monthSpent;
}
