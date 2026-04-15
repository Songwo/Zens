package com.campus.trend.campus_pulse.service;

/**
 * 异步任务服务：封装所有不影响主流程的后台操作
 * 所有方法均为 fire-and-forget，不阻塞调用方
 */
public interface AsyncTaskService {

    /** 异步处理帖子标签（创建/更新热度） */
    void processPostTagsAsync(String tagsString);

    /** 异步通知标签关注者 */
    void notifyTagFollowersAsync(String authorUserId, String postId, String postTitle, String tagsString);

    /** 异步发放经验 */
    void addExperienceAsync(String userId, int exp, String reason);

    /** 异步更新用户最后活跃时间 */
    void updateLastActiveAsync(String userId);

    /** 异步发送系统通知 */
    void sendSystemNotificationAsync(String userId, String title, String content, Object relatedId);


    /** 异步推送 WS 帖子事件（回复） */
    void pushPostRepliedAsync(String postId, Long sectionId, Integer commentCount);

    /** 异步推送 WS 帖子事件（创建） */
    void pushPostCreatedAsync(com.campus.trend.campus_pulse.entity.Post post);

    /** 异步推送 WS 帖子事件（点赞） */
    void pushPostLikedAsync(String postId, Long sectionId, Integer likeCount);

    /** 异步推送 WS 帖子事件（收藏） */
    void pushPostCollectedAsync(String postId, Long sectionId, Integer collectCount);

    /** 异步更新地区信息 */
    void updateActiveRegionAsync(String userId, String region);
}
