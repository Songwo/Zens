package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.request.NotificationBatchReq;
import com.campus.trend.campus_pulse.dto.response.NotificationListResp;
import com.campus.trend.campus_pulse.dto.response.NotificationResp;
import com.campus.trend.campus_pulse.service.NotificationService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/notification")
@Tag(name = "通知管理", description = "通知相关接口")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/list")
    @Operation(summary = "获取通知列表")
    public Result<NotificationListResp> getNotificationList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        String userId = SecurityUtils.getCurrentUserId();
        List<NotificationResp> records = notificationService.getNotificationList(userId, page, pageSize);
        long total = notificationService.countByUserId(userId);
        long unreadCount = notificationService.getUnreadCount(userId);
        return Result.success(new NotificationListResp(records, total, unreadCount));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "获取未读通知数量")
    public Result<Long> getUnreadCount() {
        return Result.success(notificationService.getUnreadCount(SecurityUtils.getCurrentUserId()));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "标记通知为已读")
    public Result<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id, SecurityUtils.getCurrentUserId());
        return Result.success();
    }

    @PutMapping("/read-all")
    @Operation(summary = "标记所有通知为已读")
    public Result<Void> markAllAsRead() {
        notificationService.markAllAsRead(SecurityUtils.getCurrentUserId());
        return Result.success();
    }

    @PutMapping("/read-batch")
    @Operation(summary = "批量标记通知已读")
    public Result<Void> markBatchAsRead(@Valid @RequestBody NotificationBatchReq req) {
        notificationService.markBatchAsRead(req.getIds(), SecurityUtils.getCurrentUserId());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除通知")
    public Result<Void> deleteNotification(@PathVariable Long id) {
        notificationService.deleteNotification(id, SecurityUtils.getCurrentUserId());
        return Result.success();
    }

    @DeleteMapping("/batch")
    @Operation(summary = "批量删除通知")
    public Result<Void> deleteBatch(@Valid @RequestBody NotificationBatchReq req) {
        notificationService.deleteBatch(req.getIds(), SecurityUtils.getCurrentUserId());
        return Result.success();
    }
}
