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
            return Collections.emptyList();
        }

        // Song：5. 查询完整的帖子信息
        return postMapper.selectBatchIds(recommendationIds);
    }

    /**
     * Song：说明
     * Song：逻辑：
     * Song：说明
     * Song：2. 推荐这些相似用户看过、但当前用户没看过的帖子
     * Song：(由于计算量大，这里暂时略过完整矩阵计算，使用简易逻辑)
     */
    @Override
    public List<Post> recommendByUserBased(String userId, int limit) {
        // Song：暂时返回空，留作扩展
        // Song：说明
        return Collections.emptyList();
    }
}
