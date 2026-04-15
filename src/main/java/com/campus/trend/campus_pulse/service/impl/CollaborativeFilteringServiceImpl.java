package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.PostCollect;
import com.campus.trend.campus_pulse.entity.PostLike;
import com.campus.trend.campus_pulse.entity.ViewLog;
import com.campus.trend.campus_pulse.mapper.PostCollectMapper;
import com.campus.trend.campus_pulse.mapper.PostLikeMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.mapper.ViewLogMapper;
import com.campus.trend.campus_pulse.service.CollaborativeFilteringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Song：协同过滤推荐服务实现类
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CollaborativeFilteringServiceImpl implements CollaborativeFilteringService {

    private final ViewLogMapper viewLogMapper;
    private final PostMapper postMapper;
    private final PostLikeMapper postLikeMapper;
    private final PostCollectMapper postCollectMapper;

    /**
     * Song：说明
     * Song：逻辑：
     * Song：说明
     * Song：2. 找出这些用户看过的其他帖子
     * Song：3. 统计这些帖子的加权分数 (浏览=1, 点赞=3, 收藏=5)
     * Song：说明
     */
    @Override
    public List<Post> recommendByItemBased(String postId, int limit) {
        // Song：说明
        // Song：说明
        List<ViewLog> whoViewedLogs = viewLogMapper.selectList(
                new LambdaQueryWrapper<ViewLog>()
                        .select(ViewLog::getUserId) // Song：说明
                        .eq(ViewLog::getPostId, postId)
                        .isNotNull(ViewLog::getUserId) // Song：排除游客
                        .last("LIMIT 100") // Song：限制样本数量，防止全表扫描
        );

        if (whoViewedLogs.isEmpty()) {
            log.debug("[协同过滤-ItemBased] 帖子 {} 无浏览记录，跳过推荐", postId);
            return Collections.emptyList();
        }

        List<String> userIds = whoViewedLogs.stream()
                .map(ViewLog::getUserId)
                .distinct()
                .collect(Collectors.toList());

        // Song：3. 统计加权分数
        Map<String, Double> postScore = new HashMap<>();

        // Song：3.1 浏览行为 (权重 1.0)
        List<ViewLog> otherViewLogs = viewLogMapper.selectList(
                new LambdaQueryWrapper<ViewLog>()
                        .select(ViewLog::getPostId)
                        .in(ViewLog::getUserId, userIds)
                        .ne(ViewLog::getPostId, postId) // Song：排除当前帖子
                        .orderByDesc(ViewLog::getCreateTime)
                        .last("LIMIT 500")
        );
        for (ViewLog log : otherViewLogs) {
            postScore.merge(log.getPostId(), 1.0, Double::sum);
        }

        // Song：3.2 点赞行为 (权重 3.0)
        List<PostLike> otherLikes = postLikeMapper.selectList(
                new LambdaQueryWrapper<PostLike>()
                        .select(PostLike::getPostId)
                        .in(PostLike::getUserId, userIds)
                        .ne(PostLike::getPostId, postId)
                        .last("LIMIT 500")
        );
        for (PostLike like : otherLikes) {
            postScore.merge(like.getPostId(), 3.0, Double::sum);
        }

        // Song：3.3 收藏行为 (权重 5.0)
        List<PostCollect> otherCollects = postCollectMapper.selectList(
                new LambdaQueryWrapper<PostCollect>()
                        .select(PostCollect::getPostId)
                        .in(PostCollect::getUserId, userIds)
                        .ne(PostCollect::getPostId, postId)
                        .last("LIMIT 500")
        );
        for (PostCollect collect : otherCollects) {
            postScore.merge(collect.getPostId(), 5.0, Double::sum);
        }

        // Song：说明
        List<String> recommendationIds = postScore.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (recommendationIds.isEmpty()) {
            log.debug("[协同过滤-ItemBased] 帖子 {} 无候选推荐", postId);
            return Collections.emptyList();
        }

        // 5. 查询完整的帖子信息
        List<Post> result = postMapper.selectBatchIds(recommendationIds);
        log.debug("[协同过滤-ItemBased] 帖子 {} 推荐 {} 篇", postId, result.size());
        return result;
    }

    /**
     * 基于用户的协同过滤推荐：
     * 1. 找出与当前用户有相似行为（点赞/收藏）的其他用户
     * 2. 推荐这些相似用户点赞/收藏过、但当前用户未看过的帖子
     */
    @Override
    public List<Post> recommendByUserBased(String userId, int limit) {
        if (userId == null || userId.isBlank()) {
            log.debug("[协同过滤-UserBased] userId 为空，跳过推荐");
            return Collections.emptyList();
        }

        // 1. 获取当前用户点赞/收藏的帖子 ID
        Set<String> myLikedPostIds = postLikeMapper.selectList(
                new LambdaQueryWrapper<PostLike>()
                        .select(PostLike::getPostId)
                        .eq(PostLike::getUserId, userId)
                        .last("LIMIT 200"))
                .stream().map(PostLike::getPostId).collect(Collectors.toSet());

        Set<String> myCollectedPostIds = postCollectMapper.selectList(
                new LambdaQueryWrapper<PostCollect>()
                        .select(PostCollect::getPostId)
                        .eq(PostCollect::getUserId, userId)
                        .last("LIMIT 200"))
                .stream().map(PostCollect::getPostId).collect(Collectors.toSet());

        Set<String> myInteractedPostIds = new HashSet<>();
        myInteractedPostIds.addAll(myLikedPostIds);
        myInteractedPostIds.addAll(myCollectedPostIds);

        if (myInteractedPostIds.isEmpty()) {
            log.debug("[协同过滤-UserBased] 用户 {} 无交互记录，跳过推荐", userId);
            return Collections.emptyList();
        }

        // 2. 找出对相同帖子有过点赞/收藏的相似用户
        Set<String> similarUserIds = new HashSet<>();
        postLikeMapper.selectList(
                new LambdaQueryWrapper<PostLike>()
                        .select(PostLike::getUserId)
                        .in(PostLike::getPostId, myInteractedPostIds)
                        .ne(PostLike::getUserId, userId)
                        .last("LIMIT 300"))
                .forEach(l -> similarUserIds.add(l.getUserId()));
        postCollectMapper.selectList(
                new LambdaQueryWrapper<PostCollect>()
                        .select(PostCollect::getPostId)
                        .in(PostCollect::getPostId, myInteractedPostIds)
                        .ne(PostCollect::getUserId, userId)
                        .last("LIMIT 300"))
                .forEach(c -> similarUserIds.add(c.getUserId()));

        if (similarUserIds.isEmpty()) {
            log.debug("[协同过滤-UserBased] 用户 {} 无相似用户，跳过推荐", userId);
            return Collections.emptyList();
        }

        // 3. 收集相似用户的交互帖子，加权评分，排除当前用户已看过的
        Set<String> myViewedPostIds = viewLogMapper.selectList(
                new LambdaQueryWrapper<ViewLog>()
                        .select(ViewLog::getPostId)
                        .eq(ViewLog::getUserId, userId)
                        .last("LIMIT 500"))
                .stream().map(ViewLog::getPostId).collect(Collectors.toSet());

        Map<String, Double> candidateScores = new HashMap<>();

        postLikeMapper.selectList(
                new LambdaQueryWrapper<PostLike>()
                        .select(PostLike::getPostId)
                        .in(PostLike::getUserId, similarUserIds)
                        .last("LIMIT 500"))
                .stream()
                .map(PostLike::getPostId)
                .filter(pid -> pid != null && !myViewedPostIds.contains(pid))
                .forEach(pid -> candidateScores.merge(pid, 3.0, Double::sum));

        postCollectMapper.selectList(
                new LambdaQueryWrapper<PostCollect>()
                        .select(PostCollect::getPostId)
                        .in(PostCollect::getUserId, similarUserIds)
                        .last("LIMIT 500"))
                .stream()
                .map(PostCollect::getPostId)
                .filter(pid -> pid != null && !myViewedPostIds.contains(pid))
                .forEach(pid -> candidateScores.merge(pid, 5.0, Double::sum));

        // 4. 取分数最高的 postId
        List<String> recommendIds = candidateScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (recommendIds.isEmpty()) {
            log.debug("[协同过滤-UserBased] 用户 {} 无候选推荐帖子", userId);
            return Collections.emptyList();
        }

        List<Post> result = postMapper.selectBatchIds(recommendIds);
        log.debug("[协同过滤-UserBased] 用户 {} 推荐 {} 篇帖子", userId, result.size());
        return result;
    }
}
