package com.campus.trend.campus_pulse.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.entity.SysNotification;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.SysNotificationService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "消息通知", description = "系统通知接口")
@RestController
@RequestMapping("/notification")
public class SysNotificationController {

    @Autowired
    private SysNotificationService notificationService;

    @Operation(summary = "获取未读数量")
    @GetMapping("/unread-count")
    public Result<Long> getUnreadCount() {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        if (authUser == null) return Result.success(0L);
        return Result.success(notificationService.getUnreadCount(authUser.getUser().getId()));
    }

    @Operation(summary = "获取通知列表")
    @GetMapping("/list")
    public Result<Page<SysNotification>> getList(@RequestParam(defaultValue = "1") Integer page,
                                                 @RequestParam(defaultValue = "10") Integer pageSize) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        Page<SysNotification> pageParam = new Page<>(page, pageSize);
        
        LambdaQueryWrapper<SysNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysNotification::getUserId, authUser.getUser().getId())
               .orderByDesc(SysNotification::getCreateTime);
               
        return Result.success(notificationService.page(pageParam, wrapper));
    }

    @Operation(summary = "标记已读")
    @PostMapping("/{id}/read")
    public Result<Void> markAsRead(@PathVariable String id) {
        notificationService.markAsRead(id);
        return Result.success();
    }

    @Operation(summary = "全部已读")
    @PostMapping("/read-all")
    public Result<Void> markAllAsRead() {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        notificationService.markAllAsRead(authUser.getUser().getId());
        return Result.success();
    }
}
