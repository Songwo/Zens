package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.dto.response.LevelInfoResp;
import com.campus.trend.campus_pulse.dto.response.ModeratorApplicationResp;
import com.campus.trend.campus_pulse.entity.ModeratorApplication;
import com.campus.trend.campus_pulse.entity.Section;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.ModeratorApplicationMapper;
import com.campus.trend.campus_pulse.mapper.SectionMapper;
import com.campus.trend.campus_pulse.service.LevelService;
import com.campus.trend.campus_pulse.service.ModeratorApplicationService;
import com.campus.trend.campus_pulse.service.NotificationService;
import com.campus.trend.campus_pulse.service.SectionModeratorService;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.utils.PermissionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModeratorApplicationServiceImpl
        extends ServiceImpl<ModeratorApplicationMapper, ModeratorApplication>
        implements ModeratorApplicationService {

    private static final int MODERATOR_APPLY_MIN_LEVEL = 5;

    private final UserService userService;
    private final LevelService levelService;
    private final SectionMapper sectionMapper;
    private final NotificationService notificationService;
    private final SectionModeratorService sectionModeratorService;

    @Override
    public void apply(String userId, Long sectionId, String reason) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "用户不存在");
        }
        if (PermissionUtils.getRoleLevel(user.getRole()) >= PermissionUtils.getRoleLevel("ROLE_ADMIN")) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "当前账号已拥有管理员权限，无需申请板块版主");
        }
        int currentLevel = resolveCurrentLevel(userId, user);
        if (currentLevel < MODERATOR_APPLY_MIN_LEVEL) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED,
                    "您的等级尚未达到 Lv" + MODERATOR_APPLY_MIN_LEVEL + "，暂不符合版主申请条件");
        }

        Section section = sectionMapper.selectById(sectionId);
        if (section == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "申请板块不存在");
        }
        if (section.getStatus() == null || section.getStatus() != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该板块当前未启用，暂不可申请");
        }
        if (sectionModeratorService.isSectionModerator(userId, sectionId)) {
            throw new BusinessException(ResultCode.FAILED, "您已经是该板块的版主，无需重复申请");
        }

        String sanitizedReason = sanitizeReason(reason);

        long pendingCount = lambdaQuery()
                .eq(ModeratorApplication::getUserId, userId)
                .eq(ModeratorApplication::getSectionId, sectionId)
                .eq(ModeratorApplication::getStatus, 0)
                .count();
        if (pendingCount > 0) {
            throw new BusinessException(ResultCode.FAILED, "您已提交过该板块的版主申请，请耐心等待审核");
        }

        ModeratorApplication app = ModeratorApplication.builder()
                .userId(userId)
                .sectionId(sectionId)
                .reason(sanitizedReason)
                .status(0)
                .createdAt(LocalDateTime.now())
                .build();
        save(app);

        notifyAdminsForNewApplication(app, user, section, currentLevel);
        log.info("用户 {} 申请成为板块 {} 的版主", userId, sectionId);
    }

    @Override
    public List<ModeratorApplicationResp> getMyApplications(String userId) {
        List<ModeratorApplication> applications = lambdaQuery()
                .eq(ModeratorApplication::getUserId, userId)
                .orderByDesc(ModeratorApplication::getCreatedAt)
                .list();
        return enrichApplications(applications);
    }

    @Override
    public List<ModeratorApplicationResp> getPendingApplications() {
        List<ModeratorApplication> applications = lambdaQuery()
                .eq(ModeratorApplication::getStatus, 0)
                .orderByAsc(ModeratorApplication::getCreatedAt)
                .list();
        return enrichApplications(applications);
    }

    @Override
    public List<ModeratorApplicationResp> getAllApplications() {
        List<ModeratorApplication> applications = lambdaQuery()
                .orderByAsc(ModeratorApplication::getStatus)
                .orderByDesc(ModeratorApplication::getCreatedAt)
                .list();
        return enrichApplications(applications);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long applicationId, String reviewNote) {
        ModeratorApplication app = getById(applicationId);
        if (app == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "申请不存在");
        }
        if (app.getStatus() != 0) {
            throw new BusinessException(ResultCode.FAILED, "该申请已被处理");
        }

        app.setStatus(1);
        app.setReviewNote(normalizeReviewNote(reviewNote));
        app.setReviewedAt(LocalDateTime.now());
        updateById(app);

        User user = userService.getById(app.getUserId());
        if (user != null) {
            log.info("用户 {} 已被批准为板块 {} 的版主", user.getUsername(), app.getSectionId());
        }

        Section section = sectionMapper.selectById(app.getSectionId());
        notifyApplicantReviewed(app, user, section, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long applicationId, String reviewNote) {
        ModeratorApplication app = getById(applicationId);
        if (app == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "申请不存在");
        }
        if (app.getStatus() != 0) {
            throw new BusinessException(ResultCode.FAILED, "该申请已被处理");
        }
        String normalizedReviewNote = normalizeReviewNote(reviewNote);
        if (!StringUtils.hasText(normalizedReviewNote)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "拒绝申请时请填写审核意见");
        }

        app.setStatus(2);
        app.setReviewNote(normalizedReviewNote);
        app.setReviewedAt(LocalDateTime.now());
        updateById(app);

        User user = userService.getById(app.getUserId());
        Section section = sectionMapper.selectById(app.getSectionId());
        notifyApplicantReviewed(app, user, section, false);
        log.info("版主申请 {} 已被拒绝", applicationId);
    }

    private int resolveCurrentLevel(String userId, User user) {
        LevelInfoResp levelInfo = levelService.getUserLevelInfo(userId);
        if (levelInfo != null && levelInfo.getLevel() != null && levelInfo.getLevel() > 0) {
            return levelInfo.getLevel();
        }
        return user.getLevel() != null ? user.getLevel() : 1;
    }

    private String sanitizeReason(String reason) {
        String sanitized = reason == null ? null : reason.trim();
        if (!StringUtils.hasText(sanitized)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "请填写申请理由");
        }
        if (sanitized.length() < 10 || sanitized.length() > 500) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "申请理由长度需在 10 到 500 个字符之间");
        }
        return sanitized;
    }

    private String normalizeReviewNote(String reviewNote) {
        if (reviewNote == null) {
            return null;
        }
        String normalized = reviewNote.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private List<ModeratorApplicationResp> enrichApplications(List<ModeratorApplication> applications) {
        if (applications == null || applications.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> userIds = applications.stream()
                .map(ModeratorApplication::getUserId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        Map<String, User> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
        Map<String, Integer> currentLevelMap = userMap.values().stream()
                .collect(Collectors.toMap(
                        User::getId,
                        user -> resolveCurrentLevel(user.getId(), user),
                        (left, right) -> left));

        Set<Long> sectionIds = applications.stream()
                .map(ModeratorApplication::getSectionId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Map<Long, Section> sectionMap = sectionIds.isEmpty()
                ? Collections.emptyMap()
                : sectionMapper.selectBatchIds(sectionIds).stream()
                .collect(Collectors.toMap(Section::getId, Function.identity(), (left, right) -> left));

        return applications.stream()
                .map(app -> toResp(
                        app,
                        userMap.get(app.getUserId()),
                        sectionMap.get(app.getSectionId()),
                        currentLevelMap.get(app.getUserId())))
                .collect(Collectors.toList());
    }

    private ModeratorApplicationResp toResp(ModeratorApplication app, User user, Section section, Integer currentLevel) {
        ModeratorApplicationResp resp = new ModeratorApplicationResp();
        resp.setId(app.getId());
        resp.setUserId(app.getUserId());
        resp.setReason(app.getReason());
        resp.setStatus(app.getStatus());
        resp.setReviewNote(app.getReviewNote());
        resp.setCreatedAt(app.getCreatedAt());
        resp.setReviewedAt(app.getReviewedAt());
        resp.setSectionId(app.getSectionId());

        if (user != null) {
            resp.setApplicantUsername(user.getUsername());
            resp.setApplicantNickname(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
            resp.setApplicantAvatar(user.getAvatar());
            resp.setApplicantEmail(user.getEmail());
            resp.setApplicantLevel(currentLevel != null && currentLevel > 0
                    ? currentLevel
                    : (user.getLevel() != null ? user.getLevel() : 1));
            resp.setApplicantRole(user.getRole());
        }

        if (section != null) {
            resp.setSectionName(section.getName());
            resp.setSectionDescription(section.getDescription());
            resp.setSectionStatus(section.getStatus());
        }
        return resp;
    }

    private void notifyAdminsForNewApplication(ModeratorApplication app, User applicant, Section section, int currentLevel) {
        String applicantName = applicant == null
                ? "未知用户"
                : (StringUtils.hasText(applicant.getNickname()) ? applicant.getNickname() : applicant.getUsername());
        String sectionName = section != null && StringUtils.hasText(section.getName())
                ? section.getName()
                : "未命名板块";
        String title = "新的版主申请待审核";
        String content = String.format("%s（Lv%d）申请成为「%s」板块版主，请尽快前往后台审核。", applicantName, currentLevel, sectionName);

        List<User> admins = userService.lambdaQuery()
                .in(User::getRole, "ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                .list();
        for (User admin : admins) {
            if (admin == null || !StringUtils.hasText(admin.getId())) {
                continue;
            }
            notificationService.createNotification(
                    admin.getId(),
                    "system",
                    title,
                    content,
                    "/admin/moderator-applications",
                    applicant != null ? applicant.getId() : null);
        }
    }

    private void notifyApplicantReviewed(ModeratorApplication app, User applicant, Section section, boolean approved) {
        if (applicant == null || !StringUtils.hasText(applicant.getId())) {
            return;
        }
        String sectionName = section != null && StringUtils.hasText(section.getName())
                ? section.getName()
                : "目标板块";
        String title = approved ? "版主申请已通过" : "版主申请未通过";
        String reviewNote = normalizeReviewNote(app.getReviewNote());
        String content = approved
                ? String.format("你申请「%s」版主的请求已通过审核。%s",
                        sectionName,
                        StringUtils.hasText(reviewNote) ? "审核备注：" + reviewNote : "请刷新页面以同步该板块的版务权限。")
                : String.format("你申请「%s」版主的请求未通过审核。%s",
                        sectionName,
                        StringUtils.hasText(reviewNote) ? "审核意见：" + reviewNote : "可根据社区要求完善后再次申请。");
        notificationService.createNotification(
                applicant.getId(),
                "system",
                title,
                content,
                null,
                null);
    }
}
