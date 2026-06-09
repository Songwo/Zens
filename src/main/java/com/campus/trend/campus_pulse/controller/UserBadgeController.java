package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.entity.UserBadge;
import com.campus.trend.campus_pulse.service.UserBadgeService;
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
        // TODO: 验证管理员权限
        userBadgeService.grantBadge(userId, badgeType, badgeCategory, badgeName, badgeDesc, grantReason, "admin");
        return Result.success("徽章授予成功");
    }

    /**
     * 撤销徽章（管理员）
     */
    @PostMapping("/revoke/{badgeId}")
    public Result<?> revokeBadge(@PathVariable Long badgeId) {
        // TODO: 验证管理员权限
        userBadgeService.revokeBadge(badgeId, "admin");
        return Result.success("徽章已撤销");
    }
}
