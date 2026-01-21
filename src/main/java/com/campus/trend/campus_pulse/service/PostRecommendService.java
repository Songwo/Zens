package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.trend.campus_pulse.dto.response.PostRecommendResp;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.Tag;
import com.campus.trend.campus_pulse.entity.UserTagRelation;

import java.util.List;

public interface PostRecommendService {

    /**
     * 获取帖子详情页底部推荐
     * 策略：同专业发布、相同分类标签、热度兜底
     */
    List<PostRecommendResp> getPostDetailRecommendations(String postId, String userId, int limit);

    /**
     * 获取混合推荐帖子列表（核心接口）
     * 包含：协同过滤、兴趣标签、热门排序
     */
    IPage<PostRecommendResp> getHybridRecommendations(String userId, int page, int pageSize);

    /**
     * 为用户推荐帖子（基于用户关注的标签）
     */
    IPage<Post> recommendPosts(String userId, int page, int pageSize);

    /**
     * 计算帖子相关度分数
     *
     * @param post     帖子
     * @param userTags 用户关注的标签
     * @return 相关度分数
     */
    double calculateScore(Post post, List<UserTagRelation> userTags);

    /**
     * 获取用户可能感兴趣的标签
     *
     * @param userId 用户ID
     * @param limit  返回数量
     * @return 推荐的标签列表
     */
    List<Tag> recommendTags(String userId, int limit);
}
