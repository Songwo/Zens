package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.config.properties.SupportContactProperties;
import com.campus.trend.campus_pulse.dto.request.UserPasswordUpdateReq;
import com.campus.trend.campus_pulse.dto.request.UserDetailUpdateReq;
import com.campus.trend.campus_pulse.dto.request.NotificationPreferenceReq;
import com.campus.trend.campus_pulse.entity.Follow;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.FollowMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.service.AuthService;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {

    public UserController(UserService userService, AuthService authService,
            FollowMapper followMapper, PostMapper postMapper,
            SupportContactProperties supportContactProperties) {
        this.userService = userService;
        this.authService = authService;
        this.followMapper = followMapper;
        this.postMapper = postMapper;
        this.supportContactProperties = supportContactProperties;
    }

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

        Long postCount = postMapper.selectCount(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getUserId, userId)
                        .eq(Post::getStatus, 1));

        Long followingCount = followMapper.selectCount(
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getFollowerId, userId));

        Long followerCount = followMapper.selectCount(
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getFolloweeId, userId));

        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
        data.put("avatar", user.getAvatar());
        data.put("bio", user.getBio());
        data.put("school", user.getSchool());
        data.put("major", user.getMajor());
        data.put("level", user.getLevel());
        data.put("roles", List.of(StringUtils.hasText(user.getRole()) ? user.getRole() : "ROLE_USER"));
        data.put("postCount", postCount != null ? postCount : 0);
        data.put("followingCount", followingCount != null ? followingCount : 0);
        data.put("followerCount", followerCount != null ? followerCount : 0);
        data.put("profileCardTheme", StringUtils.hasText(user.getProfileCardTheme()) ? user.getProfileCardTheme() : "sunset");
        data.put("quickCardTheme", StringUtils.hasText(user.getQuickCardTheme()) ? user.getQuickCardTheme() : "ocean");
        data.put("profileCardBgUrl", normalizeCardBgUrl(user.getProfileCardBgUrl()));
        data.put("quickCardBgUrl", normalizeCardBgUrl(user.getQuickCardBgUrl()));

        return Result.success(data);
    }

    @GetMapping("/search")
    public Result<?> searchUsers(@RequestParam(defaultValue = "") String keyword) {
        List<User> users = userService.lambdaQuery()
                .and(!keyword.isBlank(), q -> q
                        .like(User::getNickname, keyword)
                        .or().like(User::getUsername, keyword))
                .eq(User::getStatus, 1)
                .last("LIMIT 10")
                .list();
        List<Map<String, Object>> result = users.stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername());
            m.put("avatar", u.getAvatar());
            return m;
        }).toList();
        return Result.success(result);
    }

    @GetMapping("/all")
    public Result<?> getAll() {
        return Result.success(userService.getUsers());
    }

    @PutMapping("/avatar")
    public Result<?> updateAvatar(@RequestParam("avatar") MultipartFile file) {
        String url = userService.uploadAvatar(file);
        return Result.success(url);
    }

    @PostMapping("/update-pwd")
    public Result<?> updatePwd(@Valid @RequestBody UserPasswordUpdateReq updatePasswordRequest) {
        userService.updateUserPassword(updatePasswordRequest);
        authService.logout();
        return Result.success();
    }

    @PostMapping("/update-udetail")
    public Result<?> updateDetail(@Valid @RequestBody UserDetailUpdateReq updateUserDetailRequest) {
        userService.updateUserDetails(updateUserDetailRequest);
        return Result.success();
    }

    @GetMapping("/notification-settings")
    public Result<?> getNotificationSettings() {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.failed("请先登录");
        }
        User user = userService.getById(userId);
        if (user == null) {
            return Result.failed("用户不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("emailNotifyEnabled", user.getEmailNotifyEnabled() == null || user.getEmailNotifyEnabled() == 1);
        return Result.success(data);
    }

    @PostMapping("/notification-settings")
    public Result<?> updateNotificationSettings(@Valid @RequestBody NotificationPreferenceReq req) {
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

    /**
     * Song：获取当前用户的动态数、关注数、粉丝数
     * Song：说明
     */
    @GetMapping("/profile-stats")
    public Result<?> getProfileStats() {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.failed("请先登录");
        }

        // Song：帖子数
        Long postCount = postMapper.selectCount(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getUserId, userId)
                        .eq(Post::getStatus, 1));

        // Song：说明
        Long followingCount = followMapper.selectCount(
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getFollowerId, userId));

        // Song：说明
        Long followerCount = followMapper.selectCount(
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getFolloweeId, userId));

        Map<String, Object> data = new HashMap<>();
        data.put("postCount", postCount != null ? postCount : 0);
        data.put("followingCount", followingCount != null ? followingCount : 0);
        data.put("followerCount", followerCount != null ? followerCount : 0);

        return Result.success(data);
    }

    /**
     * Song：获取平台管理员联系信息（用于私信引导）
     * Song：说明
     */
    @GetMapping("/support-contact")
    public Result<?> getSupportContact() {
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

        Map<String, Object> data = new HashMap<>();
        data.put("id", admin.getId());
        data.put("username", admin.getUsername());
        data.put("nickname", StringUtils.hasText(admin.getNickname()) ? admin.getNickname() : supportContactProperties.getAdminDisplayName());
        data.put("avatar", admin.getAvatar());
        return Result.success(data);
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

    // Song：=================== 管理员接口 ===================

    /**
     * Song：封禁用户
     * Song：说明
     */
    @PostMapping("/ban/{id}")
    public Result<?> banUser(@PathVariable String id) {
        if (!com.campus.trend.campus_pulse.utils.PermissionUtils.isAdmin()) {
            return Result.failed("无权执行此操作");
        }
        com.campus.trend.campus_pulse.entity.User user = userService.getById(id);
        if (user == null) {
            return Result.failed("用户不存在");
        }
        // Song：不能封禁同级或上级用户
        String operatorRole = com.campus.trend.campus_pulse.utils.PermissionUtils.getCurrentUserRole();
        String targetRole = user.getRole() != null ? user.getRole() : "ROLE_USER";
        if (!com.campus.trend.campus_pulse.utils.PermissionUtils.canManageRole(operatorRole, targetRole)) {
            return Result.failed("无权封禁该用户：对方角色等级不低于您");
        }
        user.setStatus(2); // Song：2=封禁
        user.setUpdateTime(java.time.LocalDateTime.now());
        userService.updateById(user);
        return Result.success();
    }

    /**
     * Song：解封用户
     * Song：说明
     */
    @PostMapping("/unban/{id}")
    public Result<?> unbanUser(@PathVariable String id) {
        if (!com.campus.trend.campus_pulse.utils.PermissionUtils.isAdmin()) {
            return Result.failed("无权执行此操作");
        }
        com.campus.trend.campus_pulse.entity.User user = userService.getById(id);
        if (user == null) {
            return Result.failed("用户不存在");
        }
        String operatorRole = com.campus.trend.campus_pulse.utils.PermissionUtils.getCurrentUserRole();
        String targetRole = user.getRole() != null ? user.getRole() : "ROLE_USER";
        if (!com.campus.trend.campus_pulse.utils.PermissionUtils.canManageRole(operatorRole, targetRole)) {
            return Result.failed("无权解封该用户：对方角色等级不低于您");
        }
        user.setStatus(1); // Song：1=正常
        user.setUpdateTime(java.time.LocalDateTime.now());
        userService.updateById(user);
        return Result.success();
    }

    /**
     * Song：删除用户
     * Song：说明
     */
    @DeleteMapping("/{id}")
    public Result<?> deleteUser(@PathVariable String id) {
        if (!com.campus.trend.campus_pulse.utils.PermissionUtils.isAdmin()) {
            return Result.failed("无权执行此操作");
        }
        com.campus.trend.campus_pulse.entity.User user = userService.getById(id);
        if (user == null) {
            return Result.failed("用户不存在或删除失败");
        }
        String operatorRole = com.campus.trend.campus_pulse.utils.PermissionUtils.getCurrentUserRole();
        String targetRole = user.getRole() != null ? user.getRole() : "ROLE_USER";
        if (!com.campus.trend.campus_pulse.utils.PermissionUtils.canManageRole(operatorRole, targetRole)) {
            return Result.failed("无权删除该用户：对方角色等级不低于您");
        }
        boolean removed = userService.removeById(id);
        if (!removed) {
            return Result.failed("删除失败");
        }
        return Result.success();
    }

    /**
     * Song：设置用户角色
     * Song：说明
     */
    @PostMapping("/{userId}/role")
    public Result<?> assignRole(@PathVariable String userId, @RequestParam String roleCode) {
        if (!com.campus.trend.campus_pulse.utils.PermissionUtils.isAdmin()) {
            return Result.failed("无权执行此操作");
        }
        try {
            userService.assignRole(userId, roleCode);
            return Result.success();
        } catch (Exception e) {
            return Result.failed(e.getMessage());
        }
    }

}
