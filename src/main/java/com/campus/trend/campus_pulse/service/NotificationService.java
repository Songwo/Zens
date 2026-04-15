package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.response.NotificationResp;

import java.util.List;

/**
 * Song：通知服务接口
 */
public interface NotificationService {

    /**
     * Song：创建通知
     */
    void createNotification(String userId, String type, String title, String content,
            String relatedId, String relatedUserId);

    /**
     * Song：说明
     */
    void createNotification(String receiverUserId, String senderUserId,
            String senderName, String senderAvatar,
            String title, String content,
            int typeCode, String relatedPostId);

    /**
     * Song：获取用户的通知列表
     */
    List<NotificationResp> getNotificationList(String userId, int page, int size);

    /**
     * Song：获取未读通知数量
     */
    long getUnreadCount(String userId);

    /**
     * Song：标记通知为已读
     */
    void markAsRead(Long notificationId, String userId);

    /**
     * Song：标记所有通知为已读
     */
    void markAllAsRead(String userId);

    /**
     * 批量标记通知已读（仅处理当前用户自己的通知）
     */
    void markBatchAsRead(List<Long> notificationIds, String userId);

    /**
     * Song：删除通知
     */
    void deleteNotification(Long notificationId, String userId);

    /**
     * 批量删除通知（仅处理当前用户自己的通知）
     */
    void deleteBatch(List<Long> notificationIds, String userId);

    /**
     * Song：发送评论通知
     */
    void sendCommentNotification(String postAuthorId, String commenterId, String postId, String commentId, String content);

    /**
     * Song：发送点赞通知
     */
    void sendLikeNotification(String postAuthorId, String likerId, String postId);

    /**
     * Song：发送收藏通知
     */
    void sendFavoriteNotification(String postAuthorId, String favoriteUserId, String postId);

    /**
     * Song：发送关注通知
     */
    void sendFollowNotification(String followeeId, String followerId);

    /**
     * Song：发送@提醒通知
     */
    void sendMentionNotification(String mentionedUserId, String mentionerId, String postId, String commentId, String content);
}
