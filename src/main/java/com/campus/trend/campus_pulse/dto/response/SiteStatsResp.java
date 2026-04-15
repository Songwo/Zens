package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

/**
 * 站点首屏统计摘要
 */
@Data
public class SiteStatsResp {
    private long totalPosts;
    private long totalUsers;
    private long totalComments;
    private long todayPosts;
}
