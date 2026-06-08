package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.common.notification.NotificationType;
import com.campus.trend.campus_pulse.dto.response.NotificationResp;

import java.util.List;

/**
 * 通知服务接口：负责社交、系统、安全三类站内信的下发、查询与状态流转。
 */
public interface NotificationService {

    /** 原始入口（旧字符串 type 保留以兼容调用方，推荐新代码用 NotificationType 重载）。 */
    void createNotification(String userId, String type, String title, String content,
            String relatedId, String relatedUserId);

    /** 枚举版本，推荐内部优先使用。 */
    default void createNotification(String userId, NotificationType type, String title, String content,
            String relatedId, String relatedUserId) {
        createNotification(userId, type == null ? null : type.getCode(), title, content, relatedId, relatedUserId);
    }

    /** 兼容历史 typeCode：1=like, 2=reply, 3=favorite, 4=follow。 */
    void createNotification(String receiverUserId, String senderUserId,
            String senderName, String senderAvatar,
            String title, String content,
            int typeCode, String relatedPostId);

    /**
     * 实际处理通知入库与推送（供 MQ 消费者调用）
     */
    void processNotification(String userId, String type, String title, String content,
                             String relatedId, String relatedUserId);

    List<NotificationResp> getNotificationList(String userId, int page, int size);

    /** 用户全部未读通知数量。 */
    long getUnreadCount(String userId);

    /** 用户全部通知总数（用于分页 total）。 */
    long countByUserId(String userId);

    void markAsRead(Long notificationId, String userId);

    void markAllAsRead(String userId);

    void markBatchAsRead(List<Long> notificationIds, String userId);

    void deleteNotification(Long notificationId, String userId);

    void deleteBatch(List<Long> notificationIds, String userId);

    void sendCommentNotification(String postAuthorId, String commenterId, String postId, String commentId, String content);

    void sendLikeNotification(String postAuthorId, String likerId, String postId);

    void sendFavoriteNotification(String postAuthorId, String favoriteUserId, String postId);

    void sendFollowNotification(String followeeId, String followerId);

    void sendMentionNotification(String mentionedUserId, String mentionerId, String postId, String commentId, String content);

    // ==================== 安全事件 ====================

    /** 新设备登录提醒：deviceType = pc/mobile/unknown。 */
    void sendNewDeviceLoginNotification(String userId, String deviceType, String clientIp, String userAgent);

    /** 同类型设备抢占，旧会话被强制下线。 */
    void sendSessionTerminatedNotification(String userId, String deviceType, String clientIp);

    /** 密码修改成功后的提醒。 */
    void sendPasswordChangedNotification(String userId, String clientIp);

    /** 通过邮件验证码重置密码的提醒。 */
    void sendPasswordResetNotification(String userId, String clientIp);

    /** 开启 / 关闭二步验证的提醒。 */
    void sendTwoFactorToggleNotification(String userId, boolean enabled);

    /** 同一账号短时间连续登录失败达到阈值。 */
    void sendLoginFailedBurstNotification(String userId, int failedAttempts, String clientIp);
}
