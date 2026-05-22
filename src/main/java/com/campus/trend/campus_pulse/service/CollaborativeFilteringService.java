package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.entity.Post;

import java.util.List;

/**
 * Song：协同过滤推荐服务接口
 */
public interface CollaborativeFilteringService {

    /**
     * Song："看了这篇文章的人也看了..."
     *
     */
    List<Post> recommendByItemBased(String postId, int limit);

    /**
     * Song："和你兴趣相似的用户在看..."
     *
     */
    List<Post> recommendByUserBased(String userId, int limit);

}
