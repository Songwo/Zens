package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.trend.campus_pulse.dto.response.PostRecommendResp;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.Tag;
import com.campus.trend.campus_pulse.entity.UserTagRelation;

import java.util.List;

public interface PostRecommendService {

    /**
     * Song：获取帖子详情页底部推荐
     * Song：策略：同专业发布、相同分类标签、热度兜底
     */
    List<PostRecommendResp> getPostDetailRecommendations(String postId, String userId, int limit);

    /**
     * Song：获取混合推荐帖子列表（核心接口）
     * Song：包含：协同过滤、兴趣标签、热门排序
     */
    IPage<PostRecommendResp> getHybridRecommendations(String userId, int page, int pageSize);

    /**
     * Song：为用户推荐帖子（基于用户关注的标签）
     */
    IPage<Post> recommendPosts(String userId, int page, int pageSize);

    /**
     * Song：计算帖子相关度分数
     *
     * Song：说明
     * Song：说明
     * Song：说明
     */
    double calculateScore(Post post, List<UserTagRelation> userTags);

    /**
     * Song：获取用户可能感兴趣的标签
     *
     * Song：说明
     * Song：说明
     * Song：说明
     */
    List<Tag> recommendTags(String userId, int limit);
}
