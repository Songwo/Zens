package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.entity.Tag;
import com.campus.trend.campus_pulse.entity.UserTagRelation;
import com.campus.trend.campus_pulse.mapper.TagMapper;
import com.campus.trend.campus_pulse.mapper.UserTagRelationMapper;
import com.campus.trend.campus_pulse.service.UserTagRelationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserTagRelationServiceImpl extends ServiceImpl<UserTagRelationMapper, UserTagRelation>
        implements UserTagRelationService {

    private final TagMapper tagMapper;

    @Autowired
    public UserTagRelationServiceImpl(TagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    /**
     * 关注标签
     *
     * @param userId 用户ID
     * @param tagId  标签ID
     * @param score  兴趣权重（1.0-5.0）
     * @return true-关注成功, false-已经关注过
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean followTag(String userId, Long tagId, BigDecimal score) {
        // 1. 检查是否已关注
        if (isFollowing(userId, tagId)) {
            log.warn("用户 [{}] 已经关注过标签 [{}]", userId, tagId);
            return false;
        }

        // 2. 验证分数范围
        BigDecimal validScore = score;
        if (score == null || score.compareTo(BigDecimal.ONE) < 0 || score.compareTo(new BigDecimal("5.0")) > 0) {
            validScore = new BigDecimal("3.0"); // 默认3.0
        }

        // 3. 创建关注关系
        UserTagRelation relation = new UserTagRelation()
                .setUserId(userId)
                .setTagId(tagId)
                .setScore(validScore)
                .setCreateTime(LocalDateTime.now());

        boolean saved = save(relation);

        if (saved) {
            log.info("用户 [{}] 关注标签 [{}], 权重: {}", userId, tagId, validScore);
        }

        return saved;
    }

    /**
     * 取消关注标签
     *
     * @param userId 用户ID
     * @param tagId  标签ID
     * @return true-取消成功, false-未关注过
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unfollowTag(String userId, Long tagId) {
        // 1. 检查是否已关注
        if (!isFollowing(userId, tagId)) {
            log.warn("用户 [{}] 未关注过标签 [{}]", userId, tagId);
            return false;
        }

        // 2. 删除关注关系
        boolean removed = lambdaUpdate()
                .eq(UserTagRelation::getUserId, userId)
                .eq(UserTagRelation::getTagId, tagId)
                .remove();

        if (removed) {
            log.info("用户 [{}] 取消关注标签 [{}]", userId, tagId);
        }

        return removed;
    }

    /**
     * 判断是否关注
     *
     * @param userId 用户ID
     * @param tagId  标签ID
     * @return true-已关注, false-未关注
     */
    @Override
    public boolean isFollowing(String userId, Long tagId) {
        Long count = lambdaQuery()
                .eq(UserTagRelation::getUserId, userId)
                .eq(UserTagRelation::getTagId, tagId)
                .count();
        return count != null && count > 0;
    }

    /**
     * 获取用户关注的标签
     *
     * @param userId 用户ID
     * @return 标签列表
     */
    @Override
    public List<Tag> getUserFollowingTags(String userId) {
        // 1. 查询用户关注的标签关系
        List<UserTagRelation> relations = lambdaQuery()
                .eq(UserTagRelation::getUserId, userId)
                .orderByDesc(UserTagRelation::getScore) // 按权重排序
                .orderByDesc(UserTagRelation::getCreateTime)
                .list();

        if (relations.isEmpty()) {
            log.info("用户 [{}] 暂无关注的标签", userId);
            return List.of();
        }

        // 2. 提取标签ID
        List<Long> tagIds = relations.stream()
                .map(UserTagRelation::getTagId)
                .collect(Collectors.toList());

        // 3. 批量查询标签信息
        List<Tag> tags = tagMapper.selectBatchIds(tagIds);

        log.info("用户 [{}] 关注了 {} 个标签", userId, tags.size());

        return tags;
    }

    /**
     * 更新兴趣权重
     *
     * @param userId 用户ID
     * @param tagId  标签ID
     * @param score  新的权重分数
     * @return true-更新成功, false-未关注该标签
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateScore(String userId, Long tagId, BigDecimal score) {
        // 1. 检查是否已关注
        if (!isFollowing(userId, tagId)) {
            log.warn("用户 [{}] 未关注标签 [{}]，无法更新权重", userId, tagId);
            return false;
        }

        // 2. 验证分数范围
        if (score == null || score.compareTo(BigDecimal.ONE) < 0 || score.compareTo(new BigDecimal("5.0")) > 0) {
            log.warn("权重分数 [{}] 超出范围 [1.0-5.0]", score);
            return false;
        }

        // 3. 更新权重
        boolean updated = lambdaUpdate()
                .eq(UserTagRelation::getUserId, userId)
                .eq(UserTagRelation::getTagId, tagId)
                .set(UserTagRelation::getScore, score)
                .update();

        if (updated) {
            log.info("用户 [{}] 更新标签 [{}] 权重为: {}", userId, tagId, score);
        }

        return updated;
    }

    /**
     * 切换关注状态
     *
     * @param userId 用户ID
     * @param tagId  标签ID
     * @param score  兴趣权重（默认3.0）
     * @return true-已关注, false-已取消关注
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleFollow(String userId, Long tagId, BigDecimal score) {
        if (isFollowing(userId, tagId)) {
            unfollowTag(userId, tagId);
            return false;
        } else {
            followTag(userId, tagId, score != null ? score : new BigDecimal("3.0"));
            return true;
        }
    }
}
