package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.dto.request.UserPasswordUpdateReq;
import com.campus.trend.campus_pulse.dto.request.UserDetailUpdateReq;
import com.campus.trend.campus_pulse.dto.response.UserDetailResp;
import com.campus.trend.campus_pulse.dto.response.UserSimpleResp;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.SectionModeratorService;
import com.campus.trend.campus_pulse.service.UploadFileService;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final Set<String> ALLOWED_CARD_THEMES = Set.of(
            "sunset", "ocean", "forest", "aurora", "graphite", "peach", "violet");

    private final PasswordEncoder passwordEncoder;
    private final com.campus.trend.campus_pulse.service.ContentSecurityService contentSecurityService;
    private final UploadFileService uploadFileService;
    private final SectionModeratorService sectionModeratorService;

    public UserServiceImpl(PasswordEncoder passwordEncoder,
            com.campus.trend.campus_pulse.service.ContentSecurityService contentSecurityService,
            UploadFileService uploadFileService,
            SectionModeratorService sectionModeratorService) {
        this.passwordEncoder = passwordEncoder;
        this.contentSecurityService = contentSecurityService;
        this.uploadFileService = uploadFileService;
        this.sectionModeratorService = sectionModeratorService;
    }

    @Override
    public UserDetailResp getProfile() {
        // Song：说明
        AuthUser auUser = SecurityUtils.getAuthenticatedUser();

        // Song：2.获取用户详细信息
        User sysUser = searchByUsername(auUser.getUsername());

        String role = sysUser.getRole() != null ? sysUser.getRole() : "ROLE_USER";
        List<String> roleCodes = List.of(role);

        // Song：构造用户信息响应
        UserDetailResp proFileResponse = new UserDetailResp();
        proFileResponse.setId(sysUser.getId());
        proFileResponse.setUsername(sysUser.getUsername());
        proFileResponse.setEmail(sysUser.getEmail());
        proFileResponse.setAvatar(sysUser.getAvatar());
        proFileResponse.setNickname(sysUser.getNickname());
        proFileResponse.setBio(sysUser.getBio());
        proFileResponse.setMajor(sysUser.getMajor());
        proFileResponse.setLevel(sysUser.getLevel() != null ? sysUser.getLevel() : 1);
        proFileResponse.setEnrollmentYear(sysUser.getEnrollmentYear() != null ? sysUser.getEnrollmentYear() : 0);
        proFileResponse.setGender(sysUser.getGender());
        proFileResponse.setSchool(sysUser.getSchool());
        proFileResponse.setStatus(sysUser.getStatus());
        proFileResponse.setCreateTime(sysUser.getCreateTime());
        proFileResponse.setUpdateTime(sysUser.getUpdateTime());
        proFileResponse.setInterestTags(sysUser.getInterestTags());
        proFileResponse.setTwoFactorEnabled(sysUser.getTwoFactorEnabled() != null ? sysUser.getTwoFactorEnabled() : 0);
        proFileResponse.setEmailNotifyEnabled(sysUser.getEmailNotifyEnabled() != null ? sysUser.getEmailNotifyEnabled() : 1);
        proFileResponse.setGithubBound(sysUser.getGithubId() != null && !sysUser.getGithubId().isBlank());
        proFileResponse.setProfileCardTheme(resolveCardTheme(sysUser.getProfileCardTheme(), "sunset"));
        proFileResponse.setQuickCardTheme(resolveCardTheme(sysUser.getQuickCardTheme(), "ocean"));
        proFileResponse.setProfileCardBgUrl(resolveCardBgUrl(sysUser.getProfileCardBgUrl(), null));
        proFileResponse.setQuickCardBgUrl(resolveCardBgUrl(sysUser.getQuickCardBgUrl(), null));
        proFileResponse.setModeratedSectionIds(sectionModeratorService.getModeratedSectionIds(sysUser.getId()).stream().toList());
        proFileResponse.setRoles(roleCodes);

        return proFileResponse;
    }

    @Override
    public UserSimpleResp getSimpleProfile() {
        // Song：说明
        AuthUser auUser = SecurityUtils.getAuthenticatedUser();

        // Song：2.获取用户详细信息
        User sysUser = searchByUsername(auUser.getUsername());
        // Song：3.构造用户信息响应
        UserSimpleResp simpleProfileResponse = new UserSimpleResp();
        simpleProfileResponse.setId(sysUser.getId());
        simpleProfileResponse.setAvatar(sysUser.getAvatar());
        simpleProfileResponse.setNickname(sysUser.getNickname());

        return simpleProfileResponse;
    }

    @Override
    public String uploadAvatar(MultipartFile file) {
        String avatarUrl = uploadFileService.uploadAvatar(file);

        // Song：获取当前登录用户并更新数据库
        try {
            AuthUser authUser = SecurityUtils.getAuthenticatedUser();
            if (authUser != null) {
                User user = new User();
                user.setId(authUser.getUser().getId());
                user.setAvatar(avatarUrl);
                user.setUpdateTime(LocalDateTime.now());
                this.updateById(user);
            }
        } catch (Exception e) {
            log.warn("上传头像时更新用户信息失败: {}", e.getMessage());
            // Song：不抛出异常，保证上传本身是成功的，但记录日志
        }

        return avatarUrl;
    }

    @Override
    public void updateUserPassword(UserPasswordUpdateReq req) {

        // Song：1.获取当前用户
        AuthUser auUser = SecurityUtils.getAuthenticatedUser();
        User sysUser = searchByUsername(auUser.getUsername());

        // Song：2.校验旧密码
        if (!passwordEncoder.matches(req.getOldPassword(), sysUser.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "旧密码错误");
        }

        // Song：3.新旧密码不能一样
        if (passwordEncoder.matches(req.getNewPassword(), sysUser.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "新密码不能与旧密码相同");
        }

        // Song：4.设置新密码（加密）
        sysUser.setPassword(passwordEncoder.encode(req.getNewPassword()));
        sysUser.setUpdateTime(LocalDateTime.now());
        updateById(sysUser);
    }

    @Override
    public void updateUserDetails(UserDetailUpdateReq updateUserDetailRequest) {
        // Song：1.获取当前用户
        AuthUser auUser = SecurityUtils.getAuthenticatedUser();
        User sysUser = searchByUsername(auUser.getUsername());

        // Song：安全检查与敏感词过滤
        String nickname = updateUserDetailRequest.getNickname();
        if (nickname != null && contentSecurityService.containsSensitiveWords(nickname)) {
            nickname = contentSecurityService.filterSensitiveWords(nickname);
        }

        String bio = updateUserDetailRequest.getBio();
        if (bio != null && contentSecurityService.containsSensitiveWords(bio)) {
            bio = contentSecurityService.filterSensitiveWords(bio);
        }

        // Song：2.修改信息
        sysUser.setNickname(nickname);
        sysUser.setBio(bio);
        sysUser.setMajor(updateUserDetailRequest.getMajor());
        sysUser.setEnrollmentYear(updateUserDetailRequest.getEnrollmentYear());
        sysUser.setAvatar(updateUserDetailRequest.getAvatar());
        sysUser.setGender(updateUserDetailRequest.getGender());
        sysUser.setSchool(updateUserDetailRequest.getSchool());
        sysUser.setInterestTags(updateUserDetailRequest.getInterestTags());
        sysUser.setProfileCardTheme(resolveCardTheme(
                updateUserDetailRequest.getProfileCardTheme(),
                resolveCardTheme(sysUser.getProfileCardTheme(), "sunset")));
        sysUser.setQuickCardTheme(resolveCardTheme(
                updateUserDetailRequest.getQuickCardTheme(),
                resolveCardTheme(sysUser.getQuickCardTheme(), "ocean")));
        sysUser.setProfileCardBgUrl(resolveCardBgUrl(
                updateUserDetailRequest.getProfileCardBgUrl(),
                resolveCardBgUrl(sysUser.getProfileCardBgUrl(), null)));
        sysUser.setQuickCardBgUrl(resolveCardBgUrl(
                updateUserDetailRequest.getQuickCardBgUrl(),
                resolveCardBgUrl(sysUser.getQuickCardBgUrl(), null)));

        sysUser.setUpdateTime(LocalDateTime.now());

        // Song：3.执行修改
        updateById(sysUser);
    }

    @Override
    public User searchByUsername(String username) {
        return lambdaQuery().eq(User::getUsername, username).one();
    }

    @Override
    public List<User> searchByGrade(int enrollmentYear) {
        return lambdaQuery().ge(User::getEnrollmentYear, enrollmentYear).list();
    }

    @Override
    public List<User> getUsers() {
        List<User> users = this.list();
        for (User user : users) {
            String role = user.getRole() != null ? user.getRole() : "ROLE_USER";
            user.setRoles(List.of(role));
        }
        return users;
    }

    /* Song：-----------------------内置方法------------------------- */
    // Song：=================== 角色管理 ===================

    @Override
    public void assignRole(String userId, String roleCode) {
        User user = getById(userId);
        if (user == null)
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        if ("ROLE_MODERATOR".equals(roleCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "全局版主角色已停用，请通过版主申请流程分配板块版主权限");
        }

        String operatorRole = com.campus.trend.campus_pulse.utils.PermissionUtils.getCurrentUserRole();
        String currentUserId = SecurityUtils.getCurrentUserId();

        // Song：不能修改自己的角色
        if (userId.equals(currentUserId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "不能修改自己的角色");
        }

        String targetCurrentRole = user.getRole() != null ? user.getRole() : "ROLE_USER";

        // Song：操作者角色等级必须高于目标用户当前角色等级
        if (!com.campus.trend.campus_pulse.utils.PermissionUtils.canManageRole(operatorRole, targetCurrentRole)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "无权修改该用户角色：对方角色等级不低于您");
        }

        // Song：操作者只能分配等级比自己低的角色
        if (!com.campus.trend.campus_pulse.utils.PermissionUtils.canManageRole(operatorRole, roleCode)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "无权分配该角色：目标角色等级不低于您");
        }

        user.setRole(roleCode);
        updateById(user);
        log.info("为用户 {} 分配角色 {}（操作者角色: {}）", userId, roleCode, operatorRole);
    }

    // Song：=================== 资料 方法 ===================

    @Override
    public void addContribution(String userId, int amount) {
        User user = getById(userId);
        if (user == null)
            return;
        user.setContributionVal((user.getContributionVal() != null ? user.getContributionVal() : 0) + amount);
        updateById(user);
    }

    @Override
    public void updateLastActiveTime(String userId) {
        User user = getById(userId);
        if (user == null)
            return;
        user.setLastActiveTime(LocalDateTime.now());
        updateById(user);
    }

    @Override
    public void incrementLikesReceived(String userId) {
        User user = getById(userId);
        if (user == null)
            return;
        user.setTotalLikesReceived((user.getTotalLikesReceived() != null ? user.getTotalLikesReceived() : 0) + 1);
        updateById(user);
    }

    @Override
    public void decrementLikesReceived(String userId) {
        User user = getById(userId);
        if (user == null)
            return;
        user.setTotalLikesReceived(
                Math.max(0, (user.getTotalLikesReceived() != null ? user.getTotalLikesReceived() : 0) - 1));
        updateById(user);
    }

    @Override
    public void incrementTotalPosts(String userId) {
        User user = getById(userId);
        if (user == null)
            return;
        user.setTotalPosts((user.getTotalPosts() != null ? user.getTotalPosts() : 0) + 1);
        updateById(user);
    }

    @Override
    public void updatePreferredSections(String userId, String sectionId) {
        if (!org.springframework.util.StringUtils.hasText(userId) || !org.springframework.util.StringUtils.hasText(sectionId)) {
            return;
        }
        try {
            User user = getById(userId);
            if (user == null) return;
            java.util.Map<String, Double> prefs = user.getPreferredCateJson();
            if (prefs == null) {
                prefs = new java.util.HashMap<>();
            }
            // Song：每次发帖对该板块权重 +1，最高上限 100，防止无限膨胀
            double current = prefs.getOrDefault(sectionId, 0.0);
            prefs.put(sectionId, Math.min(current + 1.0, 100.0));
            user.setPreferredCateJson(prefs);
            updateById(user);
        } catch (Exception e) {
            log.warn("更新用户偏好板块失败: userId={}, sectionId={}, err={}", userId, sectionId, e.getMessage());
        }
    }

    @Override
    public void updateActiveRegion(String userId, String region) {
        User user = getById(userId);
        if (user == null)
            return;
        user.setActiveRegion(region);
        updateById(user);
    }

    private String resolveCardTheme(String raw, String fallback) {
        if (!StringUtils.hasText(raw)) {
            return fallback;
        }
        String normalized = raw.trim().toLowerCase();
        if (ALLOWED_CARD_THEMES.contains(normalized)) {
            return normalized;
        }
        return fallback;
    }

    private String resolveCardBgUrl(String raw, String fallback) {
        if (!StringUtils.hasText(raw)) {
            return fallback;
        }
        String normalized = raw.trim();
        boolean httpUrl = normalized.startsWith("http://") || normalized.startsWith("https://");
        boolean uploadUrl = normalized.startsWith("/uploads/");
        if (!httpUrl && !uploadUrl) {
            return fallback;
        }
        if (normalized.length() > 500) {
            return fallback;
        }
        if (normalized.contains("\"") || normalized.contains("'") || normalized.contains(" ")) {
            return fallback;
        }
        return normalized;
    }

}
