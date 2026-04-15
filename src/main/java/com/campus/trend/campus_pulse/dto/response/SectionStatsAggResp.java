package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

/**
 * 板块统计聚合结果。
 */
@Data
public class SectionStatsAggResp {
    private Long sectionId;
    private Long postCount;
    private Long todayCount;
    private Long heatScore;
}
