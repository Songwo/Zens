package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trend.campus_pulse.entity.SysPost;
import com.campus.trend.campus_pulse.entity.SysPostCollect;
import com.campus.trend.campus_pulse.entity.SysPostLike;
import com.campus.trend.campus_pulse.entity.SysViewLog;
import com.campus.trend.campus_pulse.mapper.SysPostCollectMapper;
import com.campus.trend.campus_pulse.mapper.SysPostLikeMapper;
import com.campus.trend.campus_pulse.mapper.SysPostMapper;
import com.campus.trend.campus_pulse.mapper.SysViewLogMapper;
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

    private final SysViewLogMapper viewLogMapper;
    private final SysPostMapper postMapper;
    private final SysPostLikeMapper postLikeMapper;
    private final SysPostCollectMapper postCollectMapper;

    /**
     * 实现 Item-Based Collaborative Filtering
     * 逻辑：
     * 1. 找出看过当前帖子(targetPost)的所有用户
     * 2. 找出这些用户看过的其他帖子
     * 3. 统计这些帖子的加权分数 (浏览=1, 点赞=3, 收藏=5)
     * 4. 排除当前帖子，返回分数最高的Top N
     */
    @Override
    public List<SysPost> recommendByItemBased(String postId, int limit) {
        // 1. 获取看过该帖子的所有用户ID (最近30天数据)
        // SQL: SELECT DISTINCT user_id FROM sys_view_log WHERE post_id = ?
        List<SysViewLog> whoViewedLogs = viewLogMapper.selectList(
                new LambdaQueryWrapper<SysViewLog>()
                        .select(SysViewLog::getUserId) // 只查userId字段优化性能
                        .eq(SysViewLog::getPostId, postId)
                        .isNotNull(SysViewLog::getUserId) // 排除游客
                        .last("LIMIT 100") // 限制样本数量，防止全表扫描
        );

        if (whoViewedLogs.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> userIds = whoViewedLogs.stream()
                .map(SysViewLog::getUserId)
                .distinct()
                .collect(Collectors.toList());

        // 3. 统计加权分数
        Map<String, Double> postScore = new HashMap<>();

        // 3.1 浏览行为 (权重 1.0)
        List<SysViewLog> otherViewLogs = viewLogMapper.selectList(
                new LambdaQueryWrapper<SysViewLog>()
                        .select(SysViewLog::getPostId)
                        .in(SysViewLog::getUserId, userIds)
                        .ne(SysViewLog::getPostId, postId) // 排除当前帖子
                        .orderByDesc(SysViewLog::getCreateTime)
                        .last("LIMIT 500")
        );
        for (SysViewLog log : otherViewLogs) {
            postScore.merge(log.getPostId(), 1.0, Double::sum);
        }

        // 3.2 点赞行为 (权重 3.0)
        List<SysPostLike> otherLikes = postLikeMapper.selectList(
                new LambdaQueryWrapper<SysPostLike>()
                        .select(SysPostLike::getPostId)
                        .in(SysPostLike::getUserId, userIds)
                        .ne(SysPostLike::getPostId, postId)
                        .last("LIMIT 500")
        );
        for (SysPostLike like : otherLikes) {
            postScore.merge(like.getPostId(), 3.0, Double::sum);
        }

        // 3.3 收藏行为 (权重 5.0)
        List<SysPostCollect> otherCollects = postCollectMapper.selectList(
                new LambdaQueryWrapper<SysPostCollect>()
                        .select(SysPostCollect::getPostId)
                        .in(SysPostCollect::getUserId, userIds)
                        .ne(SysPostCollect::getPostId, postId)
                        .last("LIMIT 500")
        );
        for (SysPostCollect collect : otherCollects) {
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
    public List<SysPost> recommendByUserBased(String userId, int limit) {
        // 暂时返回空，留作扩展
        // 生产环境通常需要离线计算 User-Item 矩阵
        return Collections.emptyList();
    }
}
