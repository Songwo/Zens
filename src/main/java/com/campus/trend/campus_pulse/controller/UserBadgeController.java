package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.entity.UserBadge;
import com.campus.trend.campus_pulse.service.UserBadgeService;
import com.campus.trend.campus_pulse.utils.PermissionUtils;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户徽章控制器
 */
@RestController
@RequestMapping("/badge")
@Slf4j
public class UserBadgeController {

    @Autowired
    private UserBadgeService userBadgeService;

    /**
     * 获取用户徽章列表
     */
    @GetMapping("/user/{userId}")
    public Result<List<UserBadge>> getUserBadges(@PathVariable String userId) {
        List<UserBadge> badges = userBadgeService.getUserBadges(userId);
        return Result.success(badges);
    }

    /**
     * 授予徽章（管理员）
     */
    @PostMapping("/grant")
    public Result<?> grantBadge(@RequestParam String userId,
                                @RequestParam String badgeType,
                                @RequestParam(required = false) String badgeCategory,
                                @RequestParam String badgeName,
                                @RequestParam(required = false) String badgeDesc,
                                @RequestParam(required = false) String grantReason) {
        if (!PermissionUtils.isAdmin()) return Result.failed("仅管理员可授予徽章");
        userBadgeService.grantBadge(userId, badgeType, badgeCategory, badgeName, badgeDesc, grantReason,
                SecurityUtils.getCurrentUserId());
        return Result.success("徽章授予成功");
    }

    /**
     * 撤销徽章（管理员）
     */
    @PostMapping("/revoke/{badgeId}")
    public Result<?> revokeBadge(@PathVariable Long badgeId) {
        if (!PermissionUtils.isAdmin()) return Result.failed("仅管理员可撤销徽章");
        userBadgeService.revokeBadge(badgeId, SecurityUtils.getCurrentUserId());
        return Result.success("徽章已撤销");
    }
}
