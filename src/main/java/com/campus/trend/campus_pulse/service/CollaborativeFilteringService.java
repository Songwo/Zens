package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.entity.Post;

import java.util.List;

/**
 * 协同过滤推荐服务接口
 */
public interface CollaborativeFilteringService {

    /**
     * 基于物品的协同过滤 (Item-Based CF)
     * "看了这篇文章的人也看了..."
     *
     * @param postId 当前帖子ID
     * @param limit  返回数量
     * @return 推荐的帖子列表
     */
    List<Post> recommendByItemBased(String postId, int limit);

    /**
     * 基于用户的协同过滤 (User-Based CF)
     * "和你兴趣相似的用户在看..."
     *
     * @param userId 当前用户ID
     * @param limit  返回数量
     * @return 推荐的帖子列表
     */
    List<Post> recommendByUserBased(String userId, int limit);

}
