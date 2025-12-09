package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trend.campus_pulse.entity.SysPost;
import com.campus.trend.campus_pulse.entity.SysTag;
import com.campus.trend.campus_pulse.entity.SysUserTagRelation;
import com.campus.trend.campus_pulse.mapper.SysPostMapper;
import com.campus.trend.campus_pulse.service.PostRecommendService;
import com.campus.trend.campus_pulse.service.TagService;
import com.campus.trend.campus_pulse.service.UserTagRelationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PostRecommendServiceImpl implements PostRecommendService {

    private final UserTagRelationService userTagRelationService;
    private final TagService tagService;
    private final SysPostMapper postMapper;

    @Autowired
    public PostRecommendServiceImpl(UserTagRelationService userTagRelationService,
            TagService tagService,
            SysPostMapper postMapper) {
        this.userTagRelationService = userTagRelationService;
        this.tagService = tagService;
        this.postMapper = postMapper;
    }

    /**
     * 为用户推荐帖子（基于用户关注的标签）
     *
     * @param userId   用户ID
     * @param page     页码
     * @param pageSize 每页大小
     * @return 推荐的帖子列表
     */
    @Override
    public IPage<SysPost> recommendPosts(String userId, int page, int pageSize) {
        // 1. 获取用户关注的标签
        List<SysUserTagRelation> userTags = userTagRelationService.lambdaQuery()
                .eq(SysUserTagRelation::getUserId, userId)
                .list();

        if (userTags.isEmpty()) {
            log.info("用户 [{}] 未关注任何标签，返回热门帖子", userId);
            // 返回热门帖子作为默认推荐
            return getHotPosts(page, pageSize);
        }

        // 2. 提取标签名称
        List<SysTag> tags = userTags.stream()
                .map(rel -> tagService.getById(rel.getTagId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Set<String> tagNames = tags.stream()
                .map(SysTag::getName)
                .collect(Collectors.toSet());

        // 3. 查询包含这些标签的帖子
        List<SysPost> candidatePosts = postMapper.selectList(null).stream()
                .filter(post -> post.getStatus() != null && post.getStatus() == 1) // 只要状态正常的
                .filter(post -> StringUtils.hasText(post.getTags())) // 有标签的
                .filter(post -> containsAnyTag(post.getTags(), tagNames)) // 包含用户关注的标签
                .collect(Collectors.toList());

        // 4. 计算相关度分数并排序
        List<PostWithScore> scoredPosts = candidatePosts.stream()
                .map(post -> {
                    double score = calculateScore(post, userTags);
                    return new PostWithScore(post, score);
                })
                .sorted((a, b) -> Double.compare(b.score, a.score)) // 降序
                .collect(Collectors.toList());

        // 5. 分页处理
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, scoredPosts.size());

        List<SysPost> paginatedPosts = new ArrayList<>();
        if (start < scoredPosts.size()) {
            paginatedPosts = scoredPosts.subList(start, end).stream()
                    .map(ps -> ps.post)
                    .collect(Collectors.toList());
        }

        Page<SysPost> resultPage = new Page<>(page, pageSize);
        resultPage.setRecords(paginatedPosts);
        resultPage.setTotal(scoredPosts.size());

        log.info("为用户 [{}] 推荐了 {} 个帖子（基于 {} 个标签）",
                userId, paginatedPosts.size(), tags.size());

        return resultPage;
    }

    /**
     * 计算帖子相关度分数
     *
     * @param post     帖子
     * @param userTags 用户关注的标签
     * @return 相关度分数
     */
    @Override
    public double calculateScore(SysPost post, List<SysUserTagRelation> userTags) {
        if (post.getTags() == null || post.getTags().isEmpty()) {
            return 0.0;
        }

        // 1. 标签匹配度分数
        double tagScore = 0.0;
        String[] postTags = post.getTags().split("[\\s#]+");
        Set<String> postTagSet = Arrays.stream(postTags)
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toSet());

        for (SysUserTagRelation userTag : userTags) {
            SysTag tag = tagService.getById(userTag.getTagId());
            if (tag != null && postTagSet.contains(tag.getName())) {
                // 用户兴趣权重 × 标签匹配度(1.0)
                tagScore += userTag.getScore().doubleValue();
            }
        }

        // 2. 时间因子（最近发布的帖子权重更高）
        double timeFactor = calculateTimeFactor(post.getCreateTime());

        // 3. 互动因子（点赞/评论/收藏越多权重越高）
        double engagementFactor = calculateEngagementFactor(post);

        // 综合分数 = 标签分数 × 时间因子 × 互动因子
        double finalScore = tagScore * timeFactor * engagementFactor;

        return finalScore;
    }

    /**
     * 获取用户可能感兴趣的标签
     *
     * @param userId 用户ID
     * @param limit  返回数量
     * @return 推荐的标签列表
     */
    @Override
    public List<SysTag> recommendTags(String userId, int limit) {
        // 获取用户已关注的标签
        List<SysTag> followedTags = userTagRelationService.getUserFollowingTags(userId);
        Set<Long> followedTagIds = followedTags.stream()
                .map(SysTag::getId)
                .collect(Collectors.toSet());

        // 获取热门标签并过滤已关注的
        List<SysTag> hotTags = tagService.getHotTags(limit * 2);
        List<SysTag> recommendations = hotTags.stream()
                .filter(tag -> !followedTagIds.contains(tag.getId()))
                .limit(limit)
                .collect(Collectors.toList());

        log.info("为用户 [{}] 推荐了 {} 个标签", userId, recommendations.size());

        return recommendations;
    }

    /**
     * 获取热门帖子
     */
    private IPage<SysPost> getHotPosts(int page, int pageSize) {
        Page<SysPost> queryPage = new Page<>(page, pageSize);
        return postMapper.selectPage(queryPage,
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysPost>()
                        .eq(SysPost::getStatus, 1)
                        .orderByDesc(SysPost::getHeatScore)
                        .orderByDesc(SysPost::getCreateTime));
    }

    /**
     * 检查帖子标签是否包含用户关注的任一标签
     */
    private boolean containsAnyTag(String postTags, Set<String> userTagNames) {
        String[] tags = postTags.split("[\\s#]+");
        for (String tag : tags) {
            String trimmed = tag.trim();
            if (!trimmed.isEmpty() && userTagNames.contains(trimmed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算时间因子（越新的帖子分数越高）
     */
    private double calculateTimeFactor(LocalDateTime createTime) {
        if (createTime == null) {
            return 0.5;
        }

        Duration age = Duration.between(createTime, LocalDateTime.now());
        long ageInDays = age.toDays();

        // 时间衰减公式: 1 / (1 + days / 7)^1.2
        // 0天=1.0, 7天=0.5, 30天=0.16
        return 1.0 / Math.pow(1 + (ageInDays / 7.0), 1.2);
    }

    /**
     * 计算互动因子（点赞/评论/收藏越多分数越高）
     */
    private double calculateEngagementFactor(SysPost post) {
        int likes = post.getLikeCount() != null ? post.getLikeCount() : 0;
        int comments = post.getCommentCount() != null ? post.getCommentCount() : 0;
        int collects = post.getCollectCount() != null ? post.getCollectCount() : 0;

        // 加权计算: 评论权重 > 收藏 > 点赞
        double engagementScore = likes + comments * 2 + collects * 1.5;

        // 归一化到1.0-2.0之间
        return 1.0 + Math.min(engagementScore / 100.0, 1.0);
    }

    /**
     * 内部类：帖子+分数
     */
    private static class PostWithScore {
        SysPost post;
        double score;

        PostWithScore(SysPost post, double score) {
            this.post = post;
            this.score = score;
        }
    }
}
