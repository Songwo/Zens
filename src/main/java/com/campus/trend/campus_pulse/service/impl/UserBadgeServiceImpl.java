package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trend.campus_pulse.entity.AnswerAdoption;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.entity.UserBadge;
import com.campus.trend.campus_pulse.mapper.AnswerAdoptionMapper;
import com.campus.trend.campus_pulse.mapper.UserBadgeMapper;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import com.campus.trend.campus_pulse.service.NotificationService;
import com.campus.trend.campus_pulse.service.UserBadgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户徽章服务实现
 */
@Slf4j
@Service
public class UserBadgeServiceImpl implements UserBadgeService {

    @Autowired
    private UserBadgeMapper userBadgeMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AnswerAdoptionMapper answerAdoptionMapper;

    @Autowired
    private NotificationService notificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantBadge(String userId, String badgeType, String badgeCategory,
                          String badgeName, String badgeDesc, String grantReason, String grantedBy) {
        // 检查是否已有相同徽章
        LambdaQueryWrapper<UserBadge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserBadge::getUserId, userId)
                .eq(UserBadge::getBadgeType, badgeType)
                .eq(UserBadge::getStatus, 1);
        if (badgeCategory != null) {
            wrapper.eq(UserBadge::getBadgeCategory, badgeCategory);
        }

        if (userBadgeMapper.selectCount(wrapper) > 0) {
            log.info("用户已拥有该徽章: userId={}, badgeType={}", userId, badgeType);
            return;
        }

        // 创建徽章
        UserBadge badge = new UserBadge()
                .setUserId(userId)
                .setBadgeType(badgeType)
                .setBadgeCategory(badgeCategory)
                .setBadgeName(badgeName)
                .setBadgeDesc(badgeDesc)
                .setStatus(1)
                .setGrantReason(grantReason)
                .setGrantedBy(grantedBy)
                .setEarnedAt(LocalDateTime.now());

        userBadgeMapper.insert(badge);

        // 发送通知
        try {
            notificationService.createNotification(
                    userId,
                    "badge_earned",
                    "获得新徽章",
                    "恭喜你获得徽章「" + badgeName + "」",
                    null,
                    "system"
            );
        } catch (Exception e) {
            log.error("发送徽章通知失败", e);
        }

        log.info("授予徽章: userId={}, badgeType={}, badgeName={}", userId, badgeType, badgeName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeBadge(Long badgeId, String operatorId) {
        UserBadge badge = userBadgeMapper.selectById(badgeId);
        if (badge != null) {
            badge.setStatus(0);
            userBadgeMapper.updateById(badge);
            log.info("撤销徽章: badgeId={}, operatorId={}", badgeId, operatorId);
        }
    }

    @Override
    public List<UserBadge> getUserBadges(String userId) {
        LambdaQueryWrapper<UserBadge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserBadge::getUserId, userId)
                .eq(UserBadge::getStatus, 1)
                .and(w -> w.isNull(UserBadge::getExpiryAt)
                        .or().gt(UserBadge::getExpiryAt, LocalDateTime.now()))
                .orderByDesc(UserBadge::getEarnedAt);
        return userBadgeMapper.selectList(wrapper);
    }

    @Override
    public void checkAndAutoGrantBadges(String userId) {
        checkEarlyBirdBadge(userId);
        checkQualityAnswerBadge(userId);
        checkExpertBadge(userId);
    }

    @Override
    public void checkEarlyBirdBadge(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }

        // 注册前1000名用户获得早期用户徽章
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(User::getCreateTime, user.getCreateTime());
        long count = userMapper.selectCount(wrapper);

        if (count < 1000) {
            grantBadge(userId, "early_bird", null,
                    "早期用户", "前1000名注册用户",
                    "注册时间：" + user.getCreateTime(), "system");
        }
    }

    @Override
    public void checkQualityAnswerBadge(String userId) {
        // 获得5次答案采纳后授予优质回答者徽章
        LambdaQueryWrapper<AnswerAdoption> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnswerAdoption::getAdoptedBy, userId);
        long adoptionCount = answerAdoptionMapper.selectCount(wrapper);

        if (adoptionCount >= 5) {
            grantBadge(userId, "quality_answer", null,
                    "优质回答者", "获得5次以上答案采纳",
                    "采纳次数：" + adoptionCount, "system");
        }
    }

    @Override
    public void checkExpertBadge(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }

        // 声望达到100且获得10次采纳后授予专家徽章
        if (user.getReputation() >= 100) {
            LambdaQueryWrapper<AnswerAdoption> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AnswerAdoption::getAdoptedBy, userId);
            long adoptionCount = answerAdoptionMapper.selectCount(wrapper);

            if (adoptionCount >= 10) {
                grantBadge(userId, "expert", null,
                        "社区专家", "声望≥100且获得10次以上采纳",
                        "声望：" + user.getReputation() + "，采纳：" + adoptionCount, "system");
            }
        }
    }
}
