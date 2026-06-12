package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trend.campus_pulse.entity.PostSubscription;

import java.util.List;

/**
 * 帖子订阅服务（主题追踪）。
 * 订阅关系是「追踪」与「邮件摘要」的共同基础：
 *  - 即时站内通知：被追踪帖有新评论时实时下发（不受邮件开关影响）；
 *  - 邮件摘要：PostDigestTask 每日聚合，受 User.emailNotifyEnabled 控制。
 */
public interface PostSubscriptionService {

    /**
     * 订阅帖子（幂等：已订阅则直接返回）。
     *
     * @param userId 订阅用户
     * @param postId 帖子ID
     * @param source 订阅来源：auto(评论/发帖自动) / manual(手动追踪)
     */
    void subscribe(String userId, String postId, String source);

    /**
     * 取消订阅（不存在时静默成功）。
     */
    void unsubscribe(String userId, String postId);

    /**
     * 当前用户是否已订阅该帖。
     */
    boolean isSubscribed(String userId, String postId);

    /**
     * 帖子的全部订阅者ID。
     */
    List<String> listSubscriberIds(String postId);

    /**
     * 我追踪的帖子（分页，按订阅时间倒序）。
     */
    Page<PostSubscription> listByUser(String userId, int page, int size);

    /**
     * 帖子有新评论时通知订阅者。
     * 排除：评论者本人、帖子作者（已有专门评论通知）、excludeUserIds（本次评论里已收到 @/回复通知的人，避免双重通知）。
     * 自身异常不向外抛（增强逻辑，不阻断评论主流程）。
     */
    void notifySubscribersOnComment(String postId, String postTitle, String postAuthorId,
                                    String commenterId, java.util.Collection<String> excludeUserIds);
}
