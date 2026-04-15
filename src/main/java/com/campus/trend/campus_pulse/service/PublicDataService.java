package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.response.PublicHomeBootstrapResp;
import com.campus.trend.campus_pulse.dto.response.SiteStatsResp;

import java.util.List;
import java.util.Map;

public interface PublicDataService {

    SiteStatsResp getSiteStats();

    List<Map<String, Object>> getHotRank(int limit, String timeRange);

    PublicHomeBootstrapResp getHomeBootstrap(int hotTagLimit, int hotRankLimit, String timeRange);
}
