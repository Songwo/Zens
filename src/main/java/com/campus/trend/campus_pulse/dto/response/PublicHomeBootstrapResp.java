package com.campus.trend.campus_pulse.dto.response;

import com.campus.trend.campus_pulse.entity.Tag;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 首页首屏公共数据聚合响应
 */
@Data
public class PublicHomeBootstrapResp {
    private List<SectionResp> activeSections;
    private List<Tag> hotTags;
    private List<Map<String, Object>> hotRank;
    private SiteStatsResp siteStats;
    private long unsolvedQaCount;
    private long todaySolvedQaCount;
    private long followedTagUpdateCount;
}
