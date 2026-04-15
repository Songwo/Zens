package com.campus.trend.campus_pulse.service;

import java.util.List;
import java.util.Map;

/**
 * 趋势统计服务接口 - 纯实时查询，不依赖缓存表
 */
public interface TrendStatService {

    Map<String, Object> getCommunityDashboard();

    Map<String, Object> getKeywordCloud();

    List<Map<String, Object>> getPostTrend(int days);

    /**
     * 获取最近7天用户增长趋势
     */
    List<Map<String, Object>> getUserTrend();

    List<Map<String, Object>> getTrendPrediction();

    Map<String, Object> getSectionPie();

    List<Map<String, Object>> getHeatRank();

    /**
     * 分页获取热度排行
     */
    List<Map<String, Object>> getHeatRank(int page, int pageSize);

    /**
     * 按时间范围分页获取热度排行
     */
    List<Map<String, Object>> getHeatRank(int page, int pageSize, String timeRange);

    Map<String, Object> getTagRelations(String keyword);

    Map<String, Object> getUserInsight(String userId);

    Map<String, Object> analyzeCodeSnippet(String code, String language);
}
