package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.entity.UserProfile;
import com.campus.trend.campus_pulse.mapper.UserProfileMapper;
import com.campus.trend.campus_pulse.service.UserProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户画像服务实现类
 */
@Service
@Slf4j
public class UserProfileServiceImpl extends ServiceImpl<UserProfileMapper, UserProfile>
        implements UserProfileService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfile createProfile(String userId) {
        // 检查是否已存在
        UserProfile existing = getById(userId);
        if (existing != null) {
            log.warn("用户 [{}] 的画像已存在，跳过创建", userId);
            return existing;
        }

        // 创建新画像
        UserProfile profile = new UserProfile()
                .setUserId(userId)
                .setReputation(100) // 初始信誉积分
                .setContributionVal(0) // 初始贡献值
                .setTotalPosts(0)
                .setTotalLikesReceived(0)
                .setLastActiveTime(LocalDateTime.now());

        save(profile);
        log.info("为用户 [{}] 创建画像成功", userId);
        return profile;
    }

    @Override
    public UserProfile getProfileByUserId(String userId) {
        return getById(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfile getOrCreateProfile(String userId) {
        UserProfile profile = getById(userId);
        if (profile == null) {
            log.info("用户 [{}] 画像不存在，自动创建", userId);
            profile = createProfile(userId);
        }
        return profile;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void incrementTotalPosts(String userId) {
        UserProfile profile = getOrCreateProfile(userId);
        int currentPosts = profile.getTotalPosts() != null ? profile.getTotalPosts() : 0;
        profile.setTotalPosts(currentPosts + 1);
        updateById(profile);
        log.debug("用户 [{}] 发帖数更新为 {}", userId, currentPosts + 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void incrementLikesReceived(String userId) {
        UserProfile profile = getOrCreateProfile(userId);
        int currentLikes = profile.getTotalLikesReceived() != null ? profile.getTotalLikesReceived() : 0;
        profile.setTotalLikesReceived(currentLikes + 1);
        updateById(profile);
        log.debug("用户 [{}] 获赞数更新为 {}", userId, currentLikes + 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void decrementLikesReceived(String userId) {
        UserProfile profile = getOrCreateProfile(userId);
        int currentLikes = profile.getTotalLikesReceived() != null ? profile.getTotalLikesReceived() : 0;
        profile.setTotalLikesReceived(Math.max(0, currentLikes - 1));
        updateById(profile);
        log.debug("用户 [{}] 获赞数更新为 {}", userId, Math.max(0, currentLikes - 1));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addContribution(String userId, int value) {
        UserProfile profile = getOrCreateProfile(userId);
        int currentVal = profile.getContributionVal() != null ? profile.getContributionVal() : 0;
        profile.setContributionVal(currentVal + value);
        updateById(profile);
        log.debug("用户 [{}] 贡献值增加 {}，当前为 {}", userId, value, currentVal + value);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLastActiveTime(String userId) {
        UserProfile profile = getOrCreateProfile(userId);
        profile.setLastActiveTime(LocalDateTime.now());
        updateById(profile);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePreferredCategories(String userId, String categoryId) {
        if (categoryId == null || categoryId.isEmpty()) {
            return;
        }

        UserProfile profile = getOrCreateProfile(userId);
        Map<String, Double> prefMap = profile.getPreferredCateJson();

        if (prefMap == null) {
            prefMap = new HashMap<>();
        }

        // 增加该分类的权重
        double currentWeight = prefMap.getOrDefault(categoryId, 0.0);
        prefMap.put(categoryId, currentWeight + 1.0);

        // 归一化权重（可选，保持总和为1.0）
        double total = prefMap.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total > 0) {
            for (Map.Entry<String, Double> entry : prefMap.entrySet()) {
                entry.setValue(entry.getValue() / total);
            }
        }

        profile.setPreferredCateJson(prefMap);
        updateById(profile);
        log.debug("用户 [{}] 偏好分类更新: {}", userId, prefMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateActiveRegion(String userId, String region) {
        if (region == null || region.isEmpty()) {
            return;
        }

        UserProfile profile = getOrCreateProfile(userId);
        profile.setActiveRegion(region);
        updateById(profile);
        log.debug("用户 [{}] 活跃地点更新为: {}", userId, region);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjustReputation(String userId, int delta) {
        UserProfile profile = getOrCreateProfile(userId);
        int currentRep = profile.getReputation() != null ? profile.getReputation() : 100;
        int newRep = Math.max(0, currentRep + delta); // 信誉不能为负
        profile.setReputation(newRep);
        updateById(profile);
        log.info("用户 [{}] 信誉积分 {} -> {}", userId, currentRep, newRep);
    }

}
