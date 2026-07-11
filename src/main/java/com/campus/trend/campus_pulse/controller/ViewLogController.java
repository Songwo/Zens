package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.dto.response.ViewHistoryDto;
import com.campus.trend.campus_pulse.service.ViewLogService;
import com.campus.trend.campus_pulse.utils.PermissionUtils;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Song：浏览日志控制器，用于数据分析和统计
 */
@RestController
@RequestMapping("/view-log")
@RequiredArgsConstructor
@Slf4j
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
        String currentUserId = SecurityUtils.getCurrentUserId();
        if (userId != null && currentUserId != null && !userId.equals(currentUserId)) {
            log.warn("忽略可疑浏览埋点 userId 参数: requestUserId={}, currentUserId={}", userId, currentUserId);
        }
        viewLogService.recordView(postId, currentUserId, ip, sanitizeDevice(device));
        return Result.success();
    }

    /**
     * Song：阅读时长心跳 —— 前端在帖子详情页定时上报停留时长，累加到用户阅读总时长与帖子平均阅读时长。
     * 用于信任等级计算（TL2/TL3 需要累计阅读时长）和热度公式加权。
     */
    @PostMapping("/heartbeat")
    public Result<?> heartbeat(@RequestParam String postId,
            @RequestParam(defaultValue = "0") int durationMs) {
        String currentUserId = SecurityUtils.getCurrentUserId();
        // Song：未登录用户的心跳也接受（仅用于帖子 avg_dwell_sec 统计），但不累加用户阅读时长
        int safeDuration = Math.min(Math.max(durationMs, 0), 600_000); // 单次最多 10 分钟，防异常值
        viewLogService.recordHeartbeat(postId, currentUserId, safeDuration);
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
        validateHistoryAccess(userId);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        List<ViewHistoryDto> history = viewLogService.getUserViewHistory(userId, safeLimit);
        return Result.success(history);
    }

    /**
     * 分页获取用户浏览历史
     */
    @GetMapping("/user-history/{userId}/page")
    public Result<?> getUserHistoryPage(@PathVariable String userId,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int pageSize) {
        validateHistoryAccess(userId);
        return Result.success(viewLogService.getUserViewHistoryPaged(userId, page, pageSize));
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
    public Result<?> cleanOldLogs(@RequestParam(defaultValue = "3650") int daysToKeep) {
        String currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null || !PermissionUtils.isUserAdmin(currentUserId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "仅管理员可以清理浏览日志");
        }
        int safeDays = Math.min(Math.max(daysToKeep, 3650), 36500);
        long deletedCount = viewLogService.cleanOldLogs(safeDays);
        return Result.success(deletedCount);
    }

    private void validateHistoryAccess(String targetUserId) {
        String currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "未登录，无法访问浏览历史");
        }

        if (!currentUserId.equals(targetUserId) && !PermissionUtils.isUserAdmin(currentUserId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "无权查看其他用户浏览历史");
        }
    }

    private String sanitizeDevice(String device) {
        if (device == null || device.isBlank()) {
            return "Unknown";
        }
        String trimmed = device.trim();
        return trimmed.length() > 50 ? trimmed.substring(0, 50) : trimmed;
    }

}
