package com.campus.trend.campus_pulse.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.config.properties.SupportContactProperties;
import com.campus.trend.campus_pulse.dto.request.AvatarUpdateReq;
import com.campus.trend.campus_pulse.dto.request.NotificationPreferenceReq;
import com.campus.trend.campus_pulse.dto.request.UserDetailUpdateReq;
import com.campus.trend.campus_pulse.dto.request.UserModeratedSectionsUpdateReq;
import com.campus.trend.campus_pulse.dto.request.UserPasswordUpdateReq;
import com.campus.trend.campus_pulse.dto.response.NotificationSettingsResp;
import com.campus.trend.campus_pulse.dto.response.SupportContactResp;
import com.campus.trend.campus_pulse.dto.response.UserProfileResp;
import com.campus.trend.campus_pulse.dto.response.UserSearchItemResp;
import com.campus.trend.campus_pulse.dto.response.UserStatsResp;
import com.campus.trend.campus_pulse.entity.Follow;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.FollowMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.service.AuthService;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.utils.PermissionUtils;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
public class UserController {

    private static final String AUDIT_STATUS_PENDING = "PENDING";
    private static final String AUDIT_STATUS_APPROVED = "APPROVED";
    private final UserService userService;
    private final AuthService authService;
    private final FollowMapper followMapper;
    private final PostMapper postMapper;
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
        User user = userService.getById(userId);
        if (user == null || (user.getStatus() != null && user.getStatus() != 1)) {
            return Result.failed("用户不存在");
        }

        long postCount = safeCount(postMapper.selectCount(buildPublicVisibleUserPostWrapper(userId)));
        long followingCount = safeCount(followMapper.selectCount(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId)));
        long followerCount = safeCount(followMapper.selectCount(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFolloweeId, userId)));

        UserProfileResp resp = new UserProfileResp(
                user.getId(),
                user.getUsername(),
                StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername(),
                user.getAvatar(),
                user.getBio(),
                user.getSchool(),
                user.getMajor(),
                user.getLevel(),
                List.of(StringUtils.hasText(user.getRole()) ? user.getRole() : "ROLE_USER"),
                postCount,
                followingCount,
                followerCount,
                StringUtils.hasText(user.getProfileCardTheme()) ? user.getProfileCardTheme() : "sunset",
                StringUtils.hasText(user.getQuickCardTheme()) ? user.getQuickCardTheme() : "ocean",
                normalizeCardBgUrl(user.getProfileCardBgUrl()),
                normalizeCardBgUrl(user.getQuickCardBgUrl()),
                user.getEnrollmentYear(),
                user.getInterestTags()
        );
        return Result.success(resp);
    }

    @GetMapping("/search")
    public Result<List<UserSearchItemResp>> searchUsers(@RequestParam(defaultValue = "") String keyword) {
        List<User> users = userService.lambdaQuery()
                .and(!keyword.isBlank(), q -> q
                        .like(User::getNickname, keyword)
                        .or().like(User::getUsername, keyword))
                .eq(User::getStatus, 1)
                .last("LIMIT 10")
                .list();

        if (users.isEmpty()) {
            return Result.success(List.of());
        }

        List<String> userIds = users.stream().map(User::getId).toList();

        Map<String, Long> postCountMap = new HashMap<>();
        postMapper.selectList(new LambdaQueryWrapper<Post>()
                        .select(Post::getUserId)
                        .in(Post::getUserId, userIds)
                        .eq(Post::getStatus, 1)
                        .and(w -> w.isNull(Post::getAuditStatus)
                                .or()
                                .eq(Post::getAuditStatus, "")
                                .or()
                                .eq(Post::getAuditStatus, AUDIT_STATUS_PENDING)
                                .or()
                                .eq(Post::getAuditStatus, AUDIT_STATUS_APPROVED)))
                .forEach(p -> postCountMap.merge(p.getUserId(), 1L, Long::sum));

        Map<String, Long> followerCountMap = new HashMap<>();
        followMapper.selectList(new LambdaQueryWrapper<Follow>()
                        .select(Follow::getFolloweeId)
                        .in(Follow::getFolloweeId, userIds))
                .forEach(f -> followerCountMap.merge(f.getFolloweeId(), 1L, Long::sum));

        List<UserSearchItemResp> result = users.stream().map(u -> new UserSearchItemResp(
                u.getId(),
                u.getUsername(),
                StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername(),
                u.getAvatar(),
                u.getBio(),
                u.getSchool(),
                postCountMap.getOrDefault(u.getId(), 0L),
                followerCountMap.getOrDefault(u.getId(), 0L)
        )).toList();
        return Result.success(result);
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
        long postCount = safeCount(postMapper.selectCount(
                new LambdaQueryWrapper<Post>().eq(Post::getUserId, userId).eq(Post::getStatus, 1)));
        long followingCount = safeCount(followMapper.selectCount(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId)));
        long followerCount = safeCount(followMapper.selectCount(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFolloweeId, userId)));
        return Result.success(new UserStatsResp(postCount, followingCount, followerCount));
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

    private LambdaQueryWrapper<Post> buildPublicVisibleUserPostWrapper(String userId) {
        return new LambdaQueryWrapper<Post>()
                .eq(Post::getUserId, userId)
                .eq(Post::getStatus, 1)
                .and(w -> w.isNull(Post::getAuditStatus)
                        .or()
                        .eq(Post::getAuditStatus, "")
                        .or()
                        .eq(Post::getAuditStatus, AUDIT_STATUS_PENDING)
                        .or()
                        .eq(Post::getAuditStatus, AUDIT_STATUS_APPROVED));
    }

    private static long safeCount(Long count) {
        return count == null ? 0L : count;
    }

    private String normalizeCardBgUrl(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        if (value.length() > 500) {
            return null;
        }
        if (value.contains("\"") || value.contains("'") || value.contains(" ")) {
            return null;
        }
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("/uploads/")) {
            return value;
        }
        return null;
    }
}
