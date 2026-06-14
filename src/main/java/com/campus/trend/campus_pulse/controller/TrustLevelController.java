package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.response.TrustInfoResp;
import com.campus.trend.campus_pulse.service.TrustLevelService;
import com.campus.trend.campus_pulse.utils.PermissionUtils;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/trust-level")
@RequiredArgsConstructor
public class TrustLevelController {

    private final TrustLevelService trustLevelService;

    /**
     * 当前登录用户的信任等级详情（含指标、阈值、进度）
     */
    @GetMapping("/info")
    public Result<TrustInfoResp> getTrustInfo() {
        String userId = SecurityUtils.getAuthenticatedUser().getUser().getId();
        return Result.success(trustLevelService.getUserTrustInfo(userId));
    }

    /**
     * 各信任等级的标签/描述/特权（公开，前端展示用，未登录也可看）
     */
    @GetMapping("/thresholds")
    public Result<List<TrustInfoResp.LevelSpec>> getThresholds() {
        return Result.success(trustLevelService.getLevelSpecs());
    }

    /**
     * 查看任意用户的信任等级详情（仅本人/管理员可看完整指标，其他用户前端仅展示等级标签）
     */
    @GetMapping("/info/{userId}")
    public Result<TrustInfoResp> getTrustInfoByUserId(@PathVariable String userId) {
        return Result.success(trustLevelService.getUserTrustInfo(userId));
    }

    /**
     * 管理员手动设置某用户信任等级（主要用于授予 TL4 领袖）
     */
    @PostMapping("/promote/{userId}")
    public Result<Void> promote(@PathVariable String userId,
                                @RequestParam int newLevel,
                                @RequestParam(required = false, defaultValue = "") String reason) {
        String operatorId = SecurityUtils.getCurrentUserId();
        trustLevelService.setTrustLevel(operatorId, userId, newLevel, reason);
        return Result.success();
    }

    /**
     * 手动触发全量信任等级重算（管理员调试用）
     */
    @PostMapping("/recalculate")
    public Result<Integer> recalculateAll() {
        String operatorId = SecurityUtils.getCurrentUserId();
        if (!PermissionUtils.isUserAdmin(operatorId)) {
            return Result.success(0);
        }
        return Result.success(trustLevelService.batchRecalculateAllActiveUsers());
    }
}
