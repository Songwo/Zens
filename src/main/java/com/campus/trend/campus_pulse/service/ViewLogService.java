package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.dto.response.ViewHistoryDto;
import com.campus.trend.campus_pulse.entity.ViewLog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Song：浏览日志服务接口 - 用于记录和分析用户浏览行为
 */
public interface ViewLogService extends IService<ViewLog> {

    /**
     * Song：记录一次浏览
     * 
     */
    void recordView(String postId, String userId, String ip, String device);

    /**
     * Song：获取帖子在指定时间段内的浏览次数
     * 
     */
    long getViewCount(String postId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * Song：获取帖子总浏览次数
     * 
     */
    long getTotalViewCount(String postId);

    /**
     * Song：获取热门帖子排行（按浏览量）
     * 
     */
    List<Map<String, Object>> getHotPostsByViews(LocalDateTime startTime, int limit);

    /**
     * Song：获取用户的浏览历史（带去重和帖子标题）
     * 
     */
    List<ViewHistoryDto> getUserViewHistory(String userId, int limit);

    /**
     * Song：分页获取用户浏览历史（去重）
     *
     */
    Map<String, Object> getUserViewHistoryPaged(String userId, int page, int pageSize);

    /**
     * Song：获取每日访问统计
     * 
     */
    List<Map<String, Object>> getDailyViewStats(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Song：获取设备类型分布
     * 
     */
    Map<String, Long> getDeviceDistribution();

    /**
     * Song：清理指定天数之前的日志（用于定时任务）
     * 
     */
    long cleanOldLogs(int daysToKeep);

}
