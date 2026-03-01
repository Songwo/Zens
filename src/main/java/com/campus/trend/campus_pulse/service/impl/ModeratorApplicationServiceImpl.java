package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.entity.ModeratorApplication;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.ModeratorApplicationMapper;
import com.campus.trend.campus_pulse.service.ModeratorApplicationService;
import com.campus.trend.campus_pulse.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class ModeratorApplicationServiceImpl
        extends ServiceImpl<ModeratorApplicationMapper, ModeratorApplication>
        implements ModeratorApplicationService {

    private final UserService userService;

    public ModeratorApplicationServiceImpl(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void apply(String userId, Long sectionId, String reason) {
        // Song：1. 检查用户等级
        User user = userService.getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (user.getLevel() == null || user.getLevel() < 5) {
            throw new RuntimeException("您的等级尚未达到 Lv5，暂不符合版主申请条件");
        }

        // Song：说明
        long pendingCount = lambdaQuery()
                .eq(ModeratorApplication::getUserId, userId)
                .eq(ModeratorApplication::getSectionId, sectionId)
                .eq(ModeratorApplication::getStatus, 0)
                .count();
        if (pendingCount > 0) {
            throw new RuntimeException("您已提交过该板块的版主申请，请耐心等待审核");
        }

        // Song：3. 创建申请
        ModeratorApplication app = ModeratorApplication.builder()
                .userId(userId)
                .sectionId(sectionId)
                .reason(reason)
                .status(0)
                .createdAt(LocalDateTime.now())
                .build();
        save(app);

        log.info("用户 {} 申请成为板块 {} 的版主", userId, sectionId);
    }

    @Override
    public List<ModeratorApplication> getMyApplications(String userId) {
        return lambdaQuery()
                .eq(ModeratorApplication::getUserId, userId)
                .orderByDesc(ModeratorApplication::getCreatedAt)
                .list();
    }

    @Override
    public List<ModeratorApplication> getPendingApplications() {
        return lambdaQuery()
                .eq(ModeratorApplication::getStatus, 0)
                .orderByAsc(ModeratorApplication::getCreatedAt)
                .list();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long applicationId, String reviewNote) {
        ModeratorApplication app = getById(applicationId);
        if (app == null) {
            throw new RuntimeException("申请不存在");
        }
        if (app.getStatus() != 0) {
            throw new RuntimeException("该申请已被处理");
        }

        // Song：更新申请状态
        app.setStatus(1);
        app.setReviewNote(reviewNote);
        app.setReviewedAt(LocalDateTime.now());
        updateById(app);

        // Song：说明
        User user = userService.getById(app.getUserId());
        if (user != null && !"ROLE_ADMIN".equals(user.getRole()) && !"ROLE_SUPER_ADMIN".equals(user.getRole())) {
            user.setRole("ROLE_MODERATOR");
            userService.updateById(user);
            log.info("用户 {} 已被批准为板块 {} 的版主", user.getUsername(), app.getSectionId());
        }
    }

    @Override
    public void reject(Long applicationId, String reviewNote) {
        ModeratorApplication app = getById(applicationId);
        if (app == null) {
            throw new RuntimeException("申请不存在");
        }
        if (app.getStatus() != 0) {
            throw new RuntimeException("该申请已被处理");
        }

        app.setStatus(2);
        app.setReviewNote(reviewNote);
        app.setReviewedAt(LocalDateTime.now());
        updateById(app);

        log.info("版主申请 {} 已被拒绝", applicationId);
    }
}
