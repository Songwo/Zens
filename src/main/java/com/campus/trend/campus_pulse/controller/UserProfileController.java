package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.request.UserProfileUpdateReq;
import com.campus.trend.campus_pulse.dto.response.UserProfileResp;
import com.campus.trend.campus_pulse.entity.UserProfile;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.UserProfileService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户画像控制器
 */
@RestController
@RequestMapping("/user-profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    /**
     * 获取当前登录用户的画像
     */
    @GetMapping
    public Result<?> getMyProfile() {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        String userId = authUser.getUser().getId();

        UserProfile profile = userProfileService.getOrCreateProfile(userId);
        return Result.success(convertToResponse(profile));
    }

    /**
     * 获取指定用户的公开画像信息
     */
    @GetMapping("/{userId}")
    public Result<?> getProfile(@PathVariable String userId) {
        UserProfile profile = userProfileService.getProfileByUserId(userId);
        if (profile == null) {
            return Result.success(null);
        }
        return Result.success(convertToResponse(profile));
    }

    /**
     * 更新当前用户的画像（手动设置部分）
     */
    @PutMapping
    public Result<?> updateMyProfile(@Valid @RequestBody UserProfileUpdateReq request) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        String userId = authUser.getUser().getId();

        // 更新活跃地点
        if (request.getActiveRegion() != null && !request.getActiveRegion().isEmpty()) {
            userProfileService.updateActiveRegion(userId, request.getActiveRegion());
        }

        return Result.success();
    }

    /**
     * 获取用户统计数据（简要）
     */
    @GetMapping("/stats")
    public Result<?> getMyStats() {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        String userId = authUser.getUser().getId();

        UserProfile profile = userProfileService.getOrCreateProfile(userId);

        // 返回简要统计数据
        return Result.success(new Object() {
            public final Integer posts = profile.getTotalPosts();
            public final Integer likes = profile.getTotalLikesReceived();
            public final Integer reputation = profile.getReputation();
            public final Integer contribution = profile.getContributionVal();
        });
    }

    /**
     * 将实体转换为响应DTO
     */
    private UserProfileResp convertToResponse(UserProfile profile) {
        UserProfileResp response = new UserProfileResp();
        response.setUserId(profile.getUserId());
        response.setReputation(profile.getReputation());
        response.setContributionVal(profile.getContributionVal());
        response.setActiveRegion(profile.getActiveRegion());
        response.setPreferredCategories(profile.getPreferredCateJson());
        response.setTotalPosts(profile.getTotalPosts());
        response.setTotalLikesReceived(profile.getTotalLikesReceived());
        response.setLastActiveTime(profile.getLastActiveTime());
        return response;
    }

}
