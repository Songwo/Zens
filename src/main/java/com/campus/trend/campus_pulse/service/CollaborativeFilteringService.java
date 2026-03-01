package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.entity.Post;

import java.util.List;

/**
 * Song：协同过滤推荐服务接口
 */
public interface CollaborativeFilteringService {

    /**
     * Song：说明
     * Song："看了这篇文章的人也看了..."
     *
     * Song：说明
     * Song：说明
     * Song：说明
     */
    List<Post> recommendByItemBased(String postId, int limit);

    /**
     * Song：说明
     * Song："和你兴趣相似的用户在看..."
     *
     * Song：说明
     * Song：说明
     * Song：说明
     */
    List<Post> recommendByUserBased(String userId, int limit);

}
