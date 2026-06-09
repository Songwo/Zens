package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.entity.UserBadge;

import java.util.List;

/**
 * 用户徽章服务接口
 */
public interface UserBadgeService {

    /**
     * 授予徽章
     * @param userId 用户ID
     * @param badgeType 徽章类型
     * @param badgeCategory 徽章分类
     * @param badgeName 徽章名称
     * @param badgeDesc 徽章描述
     * @param grantReason 授予原因
     * @param grantedBy 授予人
     */
    void grantBadge(String userId, String badgeType, String badgeCategory,
                    String badgeName, String badgeDesc, String grantReason, String grantedBy);

    /**
     * 撤销徽章
     * @param badgeId 徽章ID
     * @param operatorId 操作者ID
     */
    void revokeBadge(Long badgeId, String operatorId);

    /**
     * 获取用户徽章列表
     * @param userId 用户ID
     * @return 徽章列表
     */
    List<UserBadge> getUserBadges(String userId);

    /**
     * 检查并自动授予徽章
     * @param userId 用户ID
     */
    void checkAndAutoGrantBadges(String userId);

    /**
     * 检查早期用户徽章
     * @param userId 用户ID
     */
    void checkEarlyBirdBadge(String userId);

    /**
     * 检查优质回答徽章
     * @param userId 用户ID
     */
    void checkQualityAnswerBadge(String userId);

    /**
     * 检查专家徽章
     * @param userId 用户ID
     */
    void checkExpertBadge(String userId);
}
