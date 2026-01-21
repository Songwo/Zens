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
 * 协同过滤推荐服务实现类
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
     * 实现 Item-Based Collaborative Filtering
     * 逻辑：
     * 1. 找出看过当前帖子(targetPost)的所有用户
     * 2. 找出这些用户看过的其他帖子
     * 3. 统计这些帖子的加权分数 (浏览=1, 点赞=3, 收藏=5)
     * 4. 排除当前帖子，返回分数最高的Top N
     */
    @Override
    public List<Post> recommendByItemBased(String postId, int limit) {
        // 1. 获取看过该帖子的所有用户ID (最近30天数据)
        // SQL: SELECT DISTINCT user_id FROM sys_view_log WHERE post_id = ?
        List<ViewLog> whoViewedLogs = viewLogMapper.selectList(
                new LambdaQueryWrapper<ViewLog>()
                        .select(ViewLog::getUserId) // 只查userId字段优化性能
                        .eq(ViewLog::getPostId, postId)
                        .isNotNull(ViewLog::getUserId) // 排除游客
                        .last("LIMIT 100") // 限制样本数量，防止全表扫描
        );

        if (whoViewedLogs.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> userIds = whoViewedLogs.stream()
                .map(ViewLog::getUserId)
                .distinct()
                .collect(Collectors.toList());

        // 3. 统计加权分数
        Map<String, Double> postScore = new HashMap<>();

        // 3.1 浏览行为 (权重 1.0)
        List<ViewLog> otherViewLogs = viewLogMapper.selectList(
                new LambdaQueryWrapper<ViewLog>()
                        .select(ViewLog::getPostId)
                        .in(ViewLog::getUserId, userIds)
                        .ne(ViewLog::getPostId, postId) // 排除当前帖子
                        .orderByDesc(ViewLog::getCreateTime)
                        .last("LIMIT 500")
        );
        for (ViewLog log : otherViewLogs) {
            postScore.merge(log.getPostId(), 1.0, Double::sum);
        }

        // 3.2 点赞行为 (权重 3.0)
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

        // 3.3 收藏行为 (权重 5.0)
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

        // 4. 排序取出 Top N ID
        List<String> recommendationIds = postScore.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (recommendationIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 5. 查询完整的帖子信息
        return postMapper.selectBatchIds(recommendationIds);
    }

    /**
     * 实现 User-Based Collaborative Filtering (简化版)
     * 逻辑：
     * 1. 找出与当前用户共同浏览行为最多的Top K个相似用户
     * 2. 推荐这些相似用户看过、但当前用户没看过的帖子
     * (由于计算量大，这里暂时略过完整矩阵计算，使用简易逻辑)
     */
    @Override
    public List<Post> recommendByUserBased(String userId, int limit) {
        // 暂时返回空，留作扩展
        // 生产环境通常需要离线计算 User-Item 矩阵
        return Collections.emptyList();
    }
}
