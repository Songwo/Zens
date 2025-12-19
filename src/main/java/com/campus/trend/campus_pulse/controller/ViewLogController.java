package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.entity.SysViewLog;
import com.campus.trend.campus_pulse.service.ViewLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 浏览日志控制器 - 用于数据分析和统计
 */
@RestController
@RequestMapping("/sys-view-log")
@RequiredArgsConstructor
public class ViewLogController {

    private final ViewLogService viewLogService;

    /**
     * 记录浏览（通常由前端在查看帖子详情时调用）
     */
    @PostMapping("/record")
    public Result<?> recordView(@RequestParam String postId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String device) {
        viewLogService.recordView(postId, userId, ip, device != null ? device : "Unknown");
        return Result.success();
    }

    /**
     * 获取帖子浏览次数（指定时间段）
     */
    @GetMapping("/count")
    public Result<?> getViewCount(@RequestParam String postId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        long count = viewLogService.getViewCount(postId, startTime, endTime);
        return Result.success(count);
    }

    /**
     * 获取帖子总浏览次数
     */
    @GetMapping("/total/{postId}")
    public Result<?> getTotalViewCount(@PathVariable String postId) {
        long count = viewLogService.getTotalViewCount(postId);
        return Result.success(count);
    }

    /**
     * 获取热门帖子排行（按浏览量）
     */
    @GetMapping("/hot-posts")
    public Result<?> getHotPosts(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(defaultValue = "10") int limit) {
        List<Map<String, Object>> hotPosts = viewLogService.getHotPostsByViews(startTime, limit);
        return Result.success(hotPosts);
    }

    /**
     * 获取用户浏览历史
     */
    @GetMapping("/user-history/{userId}")
    public Result<?> getUserHistory(@PathVariable String userId,
            @RequestParam(defaultValue = "20") int limit) {
        List<SysViewLog> history = viewLogService.getUserViewHistory(userId, limit);
        return Result.success(history);
    }

    /**
     * 获取每日浏览统计
     */
    @GetMapping("/daily-stats")
    public Result<?> getDailyStats(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate) {
        List<Map<String, Object>> stats = viewLogService.getDailyViewStats(startDate, endDate);
        return Result.success(stats);
    }

    /**
     * 获取设备类型分布统计
     */
    @GetMapping("/device-distribution")
    public Result<?> getDeviceDistribution() {
        Map<String, Long> distribution = viewLogService.getDeviceDistribution();
        return Result.success(distribution);
    }

    /**
     * 清理旧日志（管理员功能）
     */
    @DeleteMapping("/clean")
    public Result<?> cleanOldLogs(@RequestParam(defaultValue = "90") int daysToKeep) {
        long deletedCount = viewLogService.cleanOldLogs(daysToKeep);
        return Result.success(deletedCount);
    }

}
