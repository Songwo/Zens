package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.config.properties.SupportContactProperties;
import com.campus.trend.campus_pulse.dto.request.AvatarUpdateReq;
import com.campus.trend.campus_pulse.dto.request.CoverConfigUpdateReq;
import com.campus.trend.campus_pulse.dto.request.NotificationPreferenceReq;
import com.campus.trend.campus_pulse.dto.request.UserBadgeUpdateReq;
import com.campus.trend.campus_pulse.dto.request.UserDetailUpdateReq;
import com.campus.trend.campus_pulse.dto.request.UserModeratedSectionsUpdateReq;
import com.campus.trend.campus_pulse.dto.request.UserPasswordUpdateReq;
import com.campus.trend.campus_pulse.dto.response.NotificationSettingsResp;
import com.campus.trend.campus_pulse.dto.response.SupportContactResp;
import com.campus.trend.campus_pulse.dto.response.UserProfileResp;
import com.campus.trend.campus_pulse.dto.response.UserSearchItemResp;
import com.campus.trend.campus_pulse.dto.response.UserStatsResp;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.service.AuthService;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.utils.PermissionUtils;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
public class UserController {

    private final UserService userService;
    private final AuthService authService;
    private final SupportContactProperties supportContactProperties;

    @GetMapping("/profile")
    public Result<?> getProfile() {
        return Result.success(userService.getProfile());
    }

    @GetMapping("/simple-profile")
    public Result<?> getSimpleProfile() {
        return Result.success(userService.getSimpleProfile());
    }

    @GetMapping("/public/{userId}")
    public Result<?> getPublicProfile(@PathVariable String userId) {
        UserProfileResp resp = userService.getPublicProfile(userId);
        if (resp == null) {
            return Result.failed("用户不存在");
        }
        return Result.success(resp);
    }

    @GetMapping("/search")
    public Result<List<UserSearchItemResp>> searchUsers(@RequestParam(defaultValue = "") String keyword) {
        return Result.success(userService.searchUsers(keyword));
    }

    @GetMapping("/all")
    public Result<?> getAll() {
        return Result.success(userService.getUsers());
    }

    @PutMapping("/avatar")
    public Result<String> updateAvatar(@Valid @RequestBody AvatarUpdateReq req) {
        return Result.success(userService.updateAvatar(req.getAvatarUrl()));
    }

    @PostMapping("/update-pwd")
    public Result<Void> updatePwd(@Valid @RequestBody UserPasswordUpdateReq updatePasswordRequest) {
        userService.updateUserPassword(updatePasswordRequest);
        authService.logout();
        return Result.success();
    }

    @PostMapping("/update-udetail")
    public Result<Void> updateDetail(@Valid @RequestBody UserDetailUpdateReq updateUserDetailRequest) {
        userService.updateUserDetails(updateUserDetailRequest);
        return Result.success();
    }

    @GetMapping("/notification-settings")
    public Result<NotificationSettingsResp> getNotificationSettings() {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.failed("请先登录");
        }
        User user = userService.getById(userId);
        if (user == null) {
            return Result.failed("用户不存在");
        }
        boolean enabled = user.getEmailNotifyEnabled() == null || user.getEmailNotifyEnabled() == 1;
        return Result.success(new NotificationSettingsResp(enabled));
    }

    @PostMapping("/notification-settings")
    public Result<Void> updateNotificationSettings(@Valid @RequestBody NotificationPreferenceReq req) {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.failed("请先登录");
        }
        User user = new User();
        user.setId(userId);
        user.setEmailNotifyEnabled(Boolean.TRUE.equals(req.getEmailNotifyEnabled()) ? 1 : 0);
        userService.updateById(user);
        return Result.success();
    }

    @GetMapping("/profile-stats")
    public Result<UserStatsResp> getProfileStats() {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.failed("请先登录");
        }
        return Result.success(userService.getProfileStats(userId));
    }

    @PutMapping("/cover")
    public Result<?> updateCover(@Valid @RequestBody CoverConfigUpdateReq req) {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.failed("请先登录");
        }
        String fit = "contain".equals(req.getFit()) ? "contain" : "cover";
        int x = clampInt(req.getX(), 0, 100, 50);
        int y = clampInt(req.getY(), 0, 100, 50);
        int height = clampInt(req.getHeight(), 120, 600, 320);
        String json = String.format("{\"fit\":\"%s\",\"x\":%d,\"y\":%d,\"height\":%d}", fit, x, y, height);

        User update = new User();
        update.setId(userId);
        update.setCoverConfig(json);
        userService.updateById(update);
        return Result.success(json);
    }

    private static int clampInt(Integer value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    @GetMapping("/support-contact")
    public Result<SupportContactResp> getSupportContact() {
        String username = supportContactProperties.getAdminUsername();
        User admin = null;
        if (StringUtils.hasText(username)) {
            admin = userService.lambdaQuery().eq(User::getUsername, username).one();
        }
        if (admin == null) {
            admin = userService.lambdaQuery()
                    .in(User::getRole, "ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                    .last("LIMIT 1")
                    .one();
        }
        if (admin == null) {
            return Result.failed("暂未配置管理员账号");
        }
        return Result.success(new SupportContactResp(
                admin.getId(),
                admin.getUsername(),
                StringUtils.hasText(admin.getNickname()) ? admin.getNickname() : supportContactProperties.getAdminDisplayName(),
                admin.getAvatar()
        ));
    }

    // =================== 管理员接口 ===================

    @PostMapping("/ban/{id}")
    public Result<?> banUser(@PathVariable String id) {
        return toggleUserStatus(id, 2, "无权封禁该用户：对方角色等级不低于您");
    }

    @PostMapping("/unban/{id}")
    public Result<?> unbanUser(@PathVariable String id) {
        return toggleUserStatus(id, 1, "无权解封该用户：对方角色等级不低于您");
    }

    @DeleteMapping("/{id}")
    public Result<?> deleteUser(@PathVariable String id) {
        if (!PermissionUtils.isAdmin()) {
            return Result.failed("无权执行此操作");
        }
        User user = userService.getById(id);
        if (user == null) {
            return Result.failed("用户不存在或删除失败");
        }
        if (!canManage(user)) {
            return Result.failed("无权删除该用户：对方角色等级不低于您");
        }
        return userService.removeById(id) ? Result.success() : Result.failed("删除失败");
    }

    @PostMapping("/{userId}/role")
    public Result<?> assignRole(@PathVariable String userId, @RequestParam String roleCode) {
        if (!PermissionUtils.isAdmin()) {
            return Result.failed("无权执行此操作");
        }
        userService.assignRole(userId, roleCode);
        return Result.success();
    }

    @PutMapping("/{userId}/badge")
    public Result<?> updateBadge(@PathVariable String userId,
                                 @Valid @RequestBody(required = false) UserBadgeUpdateReq req) {
        if (!PermissionUtils.isAdmin()) {
            return Result.failed("无权执行此操作");
        }
        User user = userService.getById(userId);
        if (user == null) {
            return Result.failed("用户不存在");
        }
        if (!canManage(user)) {
            return Result.failed("无权设置该用户徽章：对方角色等级不低于您");
        }
        String badge = req != null ? req.getBadgeText() : null;
        if (badge != null) {
            badge = badge.trim();
            if (badge.length() > 20) {
                badge = badge.substring(0, 20);
            }
            if (badge.isEmpty()) {
                badge = null;
            }
        }
        String style = req != null ? req.getBadgeStyle() : null;
        style = "rainbow".equals(style) ? "rainbow" : "solid";
        String color = req != null ? req.getBadgeColor() : null;
        if (color != null) {
            color = color.trim();
            // 只接受 #RGB / #RRGGBB / #RRGGBBAA，否则按默认色(null)处理
            if (!color.matches("^#[0-9a-fA-F]{3,8}$")) {
                color = null;
            }
        }
        // 用 lambdaUpdate().set() 而非 updateById：后者会忽略 null，无法清除徽章
        boolean ok = userService.lambdaUpdate()
                .set(User::getBadgeText, badge)
                .set(User::getBadgeColor, color)
                .set(User::getBadgeStyle, style)
                .set(User::getUpdateTime, LocalDateTime.now())
                .eq(User::getId, userId)
                .update();
        return ok ? Result.success(badge) : Result.failed("徽章设置失败");
    }

    @PutMapping("/{userId}/moderated-sections")
    public Result<?> updateModeratedSections(@PathVariable String userId,
                                             @RequestBody(required = false) UserModeratedSectionsUpdateReq req) {
        if (!PermissionUtils.isAdmin()) {
            return Result.failed("无权执行此操作");
        }
        userService.updateModeratedSections(userId, req != null ? req.getSectionIds() : List.of());
        return Result.success();
    }

    private Result<?> toggleUserStatus(String id, int targetStatus, String denyMsg) {
        if (!PermissionUtils.isAdmin()) {
            return Result.failed("无权执行此操作");
        }
        User user = userService.getById(id);
        if (user == null) {
            return Result.failed("用户不存在");
        }
        if (!canManage(user)) {
            return Result.failed(denyMsg);
        }
        user.setStatus(targetStatus);
        user.setUpdateTime(LocalDateTime.now());
        userService.updateById(user);
        return Result.success();
    }

    private boolean canManage(User target) {
        String operatorRole = PermissionUtils.getCurrentUserRole();
        String targetRole = target.getRole() != null ? target.getRole() : "ROLE_USER";
        return PermissionUtils.canManageRole(operatorRole, targetRole);
    }
}
