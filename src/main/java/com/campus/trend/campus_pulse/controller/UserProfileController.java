package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.dto.request.UpdateUserProfileRequest;
import com.campus.trend.campus_pulse.dto.response.UserProfileResponse;
import com.campus.trend.campus_pulse.entity.SysUserProfile;
import com.campus.trend.campus_pulse.security.AuthSysUser;
import com.campus.trend.campus_pulse.service.UserProfileService;
import com.campus.trend.campus_pulse.utils.GetUserDetail;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户画像控制器
 */
@RestController
@RequestMapping("/sys-user-profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    /**
     * 获取当前登录用户的画像
     */
    @GetMapping
    public Result<?> getMyProfile() {
        AuthSysUser authUser = GetUserDetail.getAuthenticatedUser();
        String userId = authUser.getSysUser().getId();

        SysUserProfile profile = userProfileService.getOrCreateProfile(userId);
        return Result.success(convertToResponse(profile));
    }

    /**
     * 获取指定用户的公开画像信息
     */
    @GetMapping("/{userId}")
    public Result<?> getProfile(@PathVariable String userId) {
        SysUserProfile profile = userProfileService.getProfileByUserId(userId);
        if (profile == null) {
            return Result.success(null);
        }
        return Result.success(convertToResponse(profile));
    }

    /**
     * 更新当前用户的画像（手动设置部分）
     */
    @PutMapping
    public Result<?> updateMyProfile(@Valid @RequestBody UpdateUserProfileRequest request) {
        AuthSysUser authUser = GetUserDetail.getAuthenticatedUser();
        String userId = authUser.getSysUser().getId();

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
        AuthSysUser authUser = GetUserDetail.getAuthenticatedUser();
        String userId = authUser.getSysUser().getId();

        SysUserProfile profile = userProfileService.getOrCreateProfile(userId);

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
    private UserProfileResponse convertToResponse(SysUserProfile profile) {
        UserProfileResponse response = new UserProfileResponse();
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
