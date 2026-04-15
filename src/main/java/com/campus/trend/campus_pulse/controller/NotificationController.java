package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.request.NotificationBatchReq;
import com.campus.trend.campus_pulse.dto.response.NotificationResp;
import com.campus.trend.campus_pulse.service.NotificationService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Song：通知控制器
 */
@Slf4j
@RestController
@RequestMapping("/notification")
@Tag(name = "通知管理", description = "通知相关接口")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @GetMapping("/list")
    @Operation(summary = "获取通知列表")
    public Result<Map<String, Object>> getNotificationList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            String userId = SecurityUtils.getCurrentUserId();
            List<NotificationResp> records = notificationService.getNotificationList(userId, page, pageSize);
            long unreadCount = notificationService.getUnreadCount(userId);

            Map<String, Object> data = new HashMap<>();
            data.put("records", records);
            data.put("total", records.size());
            data.put("unreadCount", unreadCount);
            return Result.success(data);
        } catch (Exception e) {
            log.error("获取通知列表失败", e);
            return Result.failed("获取失败");
        }
    }

    @GetMapping("/unread-count")
    @Operation(summary = "获取未读通知数量")
    public Result<Long> getUnreadCount() {
        try {
            String userId = SecurityUtils.getCurrentUserId();
            long count = notificationService.getUnreadCount(userId);
            return Result.success(count);
        } catch (Exception e) {
            log.error("获取未读数量失败", e);
            return Result.failed("获取失败");
        }
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "标记通知为已读")
    public Result<Void> markAsRead(@PathVariable Long id) {
        try {
            String userId = SecurityUtils.getCurrentUserId();
            notificationService.markAsRead(id, userId);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.failed(e.getMessage());
        } catch (Exception e) {
            log.error("标记已读失败", e);
            return Result.failed("操作失败");
        }
    }

    @PutMapping("/read-all")
    @Operation(summary = "标记所有通知为已读")
    public Result<Void> markAllAsRead() {
        try {
            String userId = SecurityUtils.getCurrentUserId();
            notificationService.markAllAsRead(userId);
            return Result.success();
        } catch (Exception e) {
            log.error("标记所有已读失败", e);
            return Result.failed("操作失败");
        }
    }

    @PutMapping("/read-batch")
    @Operation(summary = "批量标记通知已读")
    public Result<Void> markBatchAsRead(@Valid @RequestBody NotificationBatchReq req) {
        try {
            String userId = SecurityUtils.getCurrentUserId();
            notificationService.markBatchAsRead(req.getIds(), userId);
            return Result.success();
        } catch (Exception e) {
            log.error("批量标记已读失败", e);
            return Result.failed("操作失败");
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除通知")
    public Result<Void> deleteNotification(@PathVariable Long id) {
        try {
            String userId = SecurityUtils.getCurrentUserId();
            notificationService.deleteNotification(id, userId);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.failed(e.getMessage());
        } catch (Exception e) {
            log.error("删除通知失败", e);
            return Result.failed("删除失败");
        }
    }

    @DeleteMapping("/batch")
    @Operation(summary = "批量删除通知")
    public Result<Void> deleteBatch(@Valid @RequestBody NotificationBatchReq req) {
        try {
            String userId = SecurityUtils.getCurrentUserId();
            notificationService.deleteBatch(req.getIds(), userId);
            return Result.success();
        } catch (Exception e) {
            log.error("批量删除通知失败", e);
            return Result.failed("删除失败");
        }
    }
}
