package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.dto.request.UserPasswordUpdateReq;
import com.campus.trend.campus_pulse.dto.request.UserDetailUpdateReq;
import com.campus.trend.campus_pulse.dto.response.UserDetailResp;
import com.campus.trend.campus_pulse.dto.response.UserProfileResp;
import com.campus.trend.campus_pulse.dto.response.UserSearchItemResp;
import com.campus.trend.campus_pulse.dto.response.UserSimpleResp;
import com.campus.trend.campus_pulse.dto.response.UserStatsResp;
import com.campus.trend.campus_pulse.entity.Follow;
import com.campus.trend.campus_pulse.entity.ModeratorApplication;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.Section;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.FollowMapper;
import com.campus.trend.campus_pulse.mapper.ModeratorApplicationMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.mapper.SectionMapper;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.r2.R2Properties;
import com.campus.trend.campus_pulse.service.NotificationService;
import com.campus.trend.campus_pulse.service.SectionModeratorService;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final Set<String> ALLOWED_CARD_THEMES = Set.of(
            "sunset", "ocean", "forest", "aurora", "graphite", "peach", "violet");

    private final PasswordEncoder passwordEncoder;
    private final com.campus.trend.campus_pulse.service.ContentSecurityService contentSecurityService;
    private final R2Properties r2Properties;
    private final SectionModeratorService sectionModeratorService;
    private final NotificationService notificationService;
    private final ModeratorApplicationMapper moderatorApplicationMapper;
    private final SectionMapper sectionMapper;
    private final PostMapper postMapper;
    private final FollowMapper followMapper;

    public UserServiceImpl(PasswordEncoder passwordEncoder,
            com.campus.trend.campus_pulse.service.ContentSecurityService contentSecurityService,
            R2Properties r2Properties,
            SectionModeratorService sectionModeratorService,
            NotificationService notificationService,
            ModeratorApplicationMapper moderatorApplicationMapper,
            SectionMapper sectionMapper,
            PostMapper postMapper,
            FollowMapper followMapper) {
        this.passwordEncoder = passwordEncoder;
        this.contentSecurityService = contentSecurityService;
        this.r2Properties = r2Properties;
        this.sectionModeratorService = sectionModeratorService;
        this.notificationService = notificationService;
        this.moderatorApplicationMapper = moderatorApplicationMapper;
        this.sectionMapper = sectionMapper;
        this.postMapper = postMapper;
        this.followMapper = followMapper;
    }

    private static final String AUDIT_STATUS_PENDING = "PENDING";
    private static final String AUDIT_STATUS_APPROVED = "APPROVED";

    /**
     * 获取用户信息（带本地缓存）
     * 缓存key: user:info::userId
     * 过期策略: 5分钟写入过期 + 3分钟访问过期
     */
    @Cacheable(value = "user:info", key = "#userId", unless = "#result == null")
    public User getById(String userId) {
        return super.getById(userId);
    }

    /**
     * 批量获取用户（用于列表页N+1优化）
     */
    public Map<String, User> batchGetUsers(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        // 去重
        List<String> distinctIds = userIds.stream().distinct().toList();

        // 批量查询
        List<User> users = listByIds(distinctIds);

        return users.stream()
            .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }

    @Override
    public UserProfileResp getPublicProfile(String userId) {
        User user = getById(userId);
        if (user == null || (user.getStatus() != null && user.getStatus() != 1)) {
            return null;
        }

        long postCount = safeCount(postMapper.selectCount(buildPublicVisibleUserPostWrapper(userId)));
        long followingCount = safeCount(followMapper.selectCount(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId)));
        long followerCount = safeCount(followMapper.selectCount(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFolloweeId, userId)));

        return new UserProfileResp(
                user.getId(),
                user.getUsername(),
                StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername(),
                user.getAvatar(),
                user.getBio(),
                user.getSchool(),
                user.getMajor(),
                user.getLevel(),
                user.getTrustLevel() != null ? user.getTrustLevel() : 0,
                List.of(StringUtils.hasText(user.getRole()) ? user.getRole() : "ROLE_USER"),
                postCount,
                followingCount,
                followerCount,
                StringUtils.hasText(user.getProfileCardTheme()) ? user.getProfileCardTheme() : "sunset",
                StringUtils.hasText(user.getQuickCardTheme()) ? user.getQuickCardTheme() : "ocean",
                normalizeCardBgUrl(user.getProfileCardBgUrl()),
                normalizeCardBgUrl(user.getQuickCardBgUrl()),
                user.getEnrollmentYear(),
                user.getInterestTags(),
                user.getCoverConfig(),
                user.getBadgeText(),
                user.getBadgeColor(),
                user.getBadgeStyle()
        );
    }

    @Override
    public List<UserSearchItemResp> searchUsers(String keyword) {
        List<User> users = lambdaQuery()
                .and(!keyword.isBlank(), q -> q
                        .like(User::getNickname, keyword)
                        .or().like(User::getUsername, keyword))
                .eq(User::getStatus, 1)
                .last("LIMIT 10")
                .list();

        if (users.isEmpty()) {
            return List.of();
        }

        List<String> userIds = users.stream().map(User::getId).toList();

        Map<String, Long> postCountMap = new java.util.HashMap<>();
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

        Map<String, Long> followerCountMap = new java.util.HashMap<>();
        followMapper.selectList(new LambdaQueryWrapper<Follow>()
                        .select(Follow::getFolloweeId)
                        .in(Follow::getFolloweeId, userIds))
                .forEach(f -> followerCountMap.merge(f.getFolloweeId(), 1L, Long::sum));

        return users.stream().map(u -> new UserSearchItemResp(
                u.getId(),
                u.getUsername(),
                StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername(),
                u.getAvatar(),
                u.getBio(),
                u.getSchool(),
                postCountMap.getOrDefault(u.getId(), 0L),
                followerCountMap.getOrDefault(u.getId(), 0L)
        )).toList();
    }

    @Override
    public UserStatsResp getProfileStats(String userId) {
        long postCount = safeCount(postMapper.selectCount(
                new LambdaQueryWrapper<Post>().eq(Post::getUserId, userId).eq(Post::getStatus, 1)));
        long followingCount = safeCount(followMapper.selectCount(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId)));
        long followerCount = safeCount(followMapper.selectCount(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFolloweeId, userId)));
        return new UserStatsResp(postCount, followingCount, followerCount);
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

    @Override
    public UserDetailResp getProfile() {
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
        proFileResponse.setPoints(sysUser.getPoints() != null ? sysUser.getPoints() : 0);
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
        proFileResponse.setCoverConfig(sysUser.getCoverConfig());
        proFileResponse.setBadgeText(sysUser.getBadgeText());
        proFileResponse.setBadgeColor(sysUser.getBadgeColor());
        proFileResponse.setBadgeStyle(sysUser.getBadgeStyle());
        proFileResponse.setModeratedSectionIds(sectionModeratorService.getModeratedSectionIds(sysUser.getId()).stream().toList());
        proFileResponse.setRoles(roleCodes);

        return proFileResponse;
    }

    @Override
    public UserSimpleResp getSimpleProfile() {
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
    public String updateAvatar(String avatarUrl) {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ResultCode.TOKEN_MISSING, "请先登录后再更新头像");
        }
        String normalizedUrl = normalizeAvatarUrl(avatarUrl);

        User user = new User();
        user.setId(userId);
        user.setAvatar(normalizedUrl);
        user.setUpdateTime(LocalDateTime.now());
        this.updateById(user);

        return normalizedUrl;
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

        // 密码修改成功后发送安全通知
        try {
            notificationService.sendPasswordChangedNotification(sysUser.getId(), null);
        } catch (Exception ex) {
            log.warn("密码修改通知发送失败: userId={}, err={}", sysUser.getId(), ex.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "user:info", key = "T(com.campus.trend.campus_pulse.utils.SecurityUtils).getCurrentUserId()")
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
        List<String> userIds = users.stream()
                .map(User::getId)
                .filter(StringUtils::hasText)
                .toList();
        Map<String, List<Long>> moderatedSectionIdsByUser = userIds.isEmpty()
                ? Map.of()
                : moderatorApplicationMapper.selectList(new LambdaQueryWrapper<ModeratorApplication>()
                                .select(ModeratorApplication::getUserId, ModeratorApplication::getSectionId)
                                .in(ModeratorApplication::getUserId, userIds)
                                .eq(ModeratorApplication::getStatus, 1))
                        .stream()
                        .filter(app -> StringUtils.hasText(app.getUserId()) && app.getSectionId() != null)
                        .collect(Collectors.groupingBy(
                                ModeratorApplication::getUserId,
                                Collectors.mapping(
                                        ModeratorApplication::getSectionId,
                                        Collectors.collectingAndThen(
                                                Collectors.toCollection(LinkedHashSet::new),
                                                ArrayList::new))));
        for (User user : users) {
            String role = user.getRole() != null ? user.getRole() : "ROLE_USER";
            user.setRoles(List.of(role));
            user.setModeratedSectionIds(moderatedSectionIdsByUser.getOrDefault(user.getId(), List.of()));
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateModeratedSections(String userId, List<Long> sectionIds) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        String operatorRole = com.campus.trend.campus_pulse.utils.PermissionUtils.getCurrentUserRole();
        String currentUserId = SecurityUtils.getCurrentUserId();
        if (userId.equals(currentUserId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "不能修改自己的版主板块");
        }

        String targetCurrentRole = user.getRole() != null ? user.getRole() : "ROLE_USER";
        if (!com.campus.trend.campus_pulse.utils.PermissionUtils.canManageRole(operatorRole, targetCurrentRole)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "无权修改该用户版主板块：对方角色等级不低于您");
        }

        Set<Long> targetSectionIds = sectionIds == null
                ? Set.of()
                : sectionIds.stream()
                    .filter(id -> id != null && id > 0)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!targetSectionIds.isEmpty()) {
            Set<Long> existingSectionIds = sectionMapper.selectBatchIds(targetSectionIds).stream()
                    .map(Section::getId)
                    .collect(Collectors.toSet());
            if (existingSectionIds.size() != targetSectionIds.size()) {
                throw new BusinessException(ResultCode.SECTION_NOT_FOUND, "包含不存在的板块");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        List<ModeratorApplication> existingApplications = moderatorApplicationMapper.selectList(
                new LambdaQueryWrapper<ModeratorApplication>()
                        .eq(ModeratorApplication::getUserId, userId));
        Set<Long> existingSectionIds = existingApplications.stream()
                .map(ModeratorApplication::getSectionId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());

        for (ModeratorApplication app : existingApplications) {
            Long sectionId = app.getSectionId();
            if (sectionId == null) {
                continue;
            }
            if (targetSectionIds.contains(sectionId)) {
                app.setStatus(1);
                if (!StringUtils.hasText(app.getReason())) {
                    app.setReason("管理员直接授权");
                }
                app.setReviewNote("用户管理直接设置为板块版主");
                app.setReviewedAt(now);
                moderatorApplicationMapper.updateById(app);
            } else if (app.getStatus() != null && app.getStatus() == 1) {
                app.setStatus(2);
                app.setReviewNote("用户管理取消板块版主身份");
                app.setReviewedAt(now);
                moderatorApplicationMapper.updateById(app);
            }
        }

        for (Long sectionId : targetSectionIds) {
            if (existingSectionIds.contains(sectionId)) {
                continue;
            }
            ModeratorApplication app = ModeratorApplication.builder()
                    .userId(userId)
                    .sectionId(sectionId)
                    .reason("管理员直接授权")
                    .status(1)
                    .reviewNote("用户管理直接设置为板块版主")
                    .createdAt(now)
                    .reviewedAt(now)
                    .build();
            moderatorApplicationMapper.insert(app);
        }

        log.info("为用户 {} 设置板块版主范围: {}", userId, targetSectionIds);
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

    private String normalizeAvatarUrl(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "头像地址不能为空");
        }
        String normalized = raw.trim();
        if (normalized.length() > 500 || normalized.contains("\"") || normalized.contains("'") || normalized.contains(" ")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "头像地址非法");
        }
        String baseUrl = r2Properties.getPublicBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "R2 公网地址未配置");
        }
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (!normalized.startsWith(normalizedBase + "/")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "头像必须使用系统 R2 域名");
        }
        return normalized;
    }

}
