package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.entity.SysUserProfile;

/**
 * 用户画像服务接口
 */
public interface UserProfileService extends IService<SysUserProfile> {

    /**
     * 创建用户画像（初始化）
     * 
     * @param userId 用户ID
     * @return 创建的画像
     */
    SysUserProfile createProfile(String userId);

    /**
     * 根据用户ID获取画像
     * 
     * @param userId 用户ID
     * @return 用户画像，不存在返回null
     */
    SysUserProfile getProfileByUserId(String userId);

    /**
     * 获取或创建用户画像（懒加载模式）
     * 
     * @param userId 用户ID
     * @return 用户画像
     */
    SysUserProfile getOrCreateProfile(String userId);

    /**
     * 增加发帖数
     * 
     * @param userId 用户ID
     */
    void incrementTotalPosts(String userId);

    /**
     * 增加获赞数（帖子作者收到点赞）
     * 
     * @param userId 帖子作者ID
     */
    void incrementLikesReceived(String userId);

    /**
     * 减少获赞数（取消点赞时）
     * 
     * @param userId 帖子作者ID
     */
    void decrementLikesReceived(String userId);

    /**
     * 增加贡献值
     * 
     * @param userId 用户ID
     * @param value  增加的值
     */
    void addContribution(String userId, int value);

    /**
     * 更新最后活跃时间
     * 
     * @param userId 用户ID
     */
    void updateLastActiveTime(String userId);

    /**
     * 更新偏好分类权重
     * 
     * @param userId     用户ID
     * @param categoryId 分类ID
     */
    void updatePreferredCategories(String userId, String categoryId);

    /**
     * 更新活跃地点
     * 
     * @param userId 用户ID
     * @param region 地点名称
     */
    void updateActiveRegion(String userId, String region);

    /**
     * 增加信誉积分
     * 
     * @param userId 用户ID
     * @param delta  变化值（可为负数扣分）
     */
    void adjustReputation(String userId, int delta);

}
