package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.event.PostEvent;
import com.campus.trend.campus_pulse.entity.Post;

/**
 * Song：帖子事件推送服务
 */
public interface PostEventService {

    /**
     * Song：推送新帖创建事件
     */
    void pushPostCreated(Post post);

    /**
     * Song：推送新回复事件
     */
    void pushPostReplied(String postId, Long sectionId);

    /**
     * 推送新回复事件（携带最新评论数）
     */
    void pushPostReplied(String postId, Long sectionId, Integer commentCount);

    /**
     * Song：推送浏览量更新事件
     */
    void pushPostViewed(String postId, Long sectionId, Integer viewCount, java.time.LocalDateTime lastActivityAt);

    /**
     * Song：推送点赞数更新事件
     */
    void pushPostLiked(String postId, Long sectionId, Integer likeCount);

    /**
     * Song：推送收藏数更新事件
     */
    void pushPostCollected(String postId, Long sectionId, Integer collectCount);

    /**
     * Song：推送置顶状态更新事件
     */
    void pushPinUpdated(Post post);

    /**
     * Song：推送通用事件
     */
    void pushEvent(PostEvent event);
}
