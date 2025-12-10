package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.entity.SysViewLog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 浏览日志服务接口 - 用于记录和分析用户浏览行为
 */
public interface ViewLogService extends IService<SysViewLog> {

    /**
     * 记录一次浏览
     * 
     * @param postId 帖子ID
     * @param userId 用户ID（游客可为null）
     * @param ip     访问IP
     * @param device 设备类型
     */
    void recordView(String postId, String userId, String ip, String device);

    /**
     * 获取帖子在指定时间段内的浏览次数
     * 
     * @param postId    帖子ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 浏览次数
     */
    long getViewCount(String postId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取帖子总浏览次数
     * 
     * @param postId 帖子ID
     * @return 浏览次数
     */
    long getTotalViewCount(String postId);

    /**
     * 获取热门帖子排行（按浏览量）
     * 
     * @param startTime 统计开始时间
     * @param limit     返回数量
     * @return 帖子ID和浏览次数的Map列表
     */
    List<Map<String, Object>> getHotPostsByViews(LocalDateTime startTime, int limit);

    /**
     * 获取用户的浏览历史
     * 
     * @param userId 用户ID
     * @param limit  返回数量
     * @return 浏览记录列表
     */
    List<SysViewLog> getUserViewHistory(String userId, int limit);

    /**
     * 获取每日访问统计
     * 
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 每日访问量列表
     */
    List<Map<String, Object>> getDailyViewStats(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * 获取设备类型分布
     * 
     * @return 设备类型统计
     */
    Map<String, Long> getDeviceDistribution();

    /**
     * 清理指定天数之前的日志（用于定时任务）
     * 
     * @param daysToKeep 保留天数
     * @return 删除的记录数
     */
    long cleanOldLogs(int daysToKeep);

}
