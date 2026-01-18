package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.entity.SysAnnouncement;
import com.campus.trend.campus_pulse.service.AnnouncementService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "系统公告与弹窗")
@RestController
@RequestMapping("/announcement")
public class AnnouncementController {

    @Autowired
    private AnnouncementService announcementService;

    @Operation(summary = "获取待显示的欢迎弹窗")
    @GetMapping("/pending-popup")
    public Result<SysAnnouncement> getPopup() {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return Result.success(null);
        return Result.success(announcementService.getPendingPopup(userId));
    }

    @Operation(summary = "标记弹窗已读")
    @PostMapping("/mark-seen/{id}")
    public Result<Void> markSeen(@PathVariable Long id) {
        String userId = SecurityUtils.getCurrentUserId();
        announcementService.markAsSeen(userId, id);
        return Result.success();
    }

    @Operation(summary = "发布公告 (管理员)")
    @PostMapping("/save")
    public Result<Void> save(@RequestBody SysAnnouncement announcement) {
        announcementService.saveAnnouncement(announcement);
        return Result.success();
    }
}
