package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trend.campus_pulse.entity.PostSubscription;
import com.campus.trend.campus_pulse.mapper.PostSubscriptionMapper;
import com.campus.trend.campus_pulse.service.NotificationService;
import com.campus.trend.campus_pulse.service.PostSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 帖子订阅服务实现。
 * 订阅幂等靠唯一约束 (user_id, post_id) 兜底；通知逻辑全程不向外抛异常。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostSubscriptionServiceImpl implements PostSubscriptionService {

    /** 站内通知类型：被追踪帖有新动态 */
    public static final String NOTIFY_TYPE_POST_ACTIVITY = "post_activity";

    private final PostSubscriptionMapper postSubscriptionMapper;
    private final NotificationService notificationService;

    @Override
    public void subscribe(String userId, String postId, String source) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(postId)) {
            return;
        }
        if (isSubscribed(userId, postId)) {
            return;
        }
        PostSubscription sub = new PostSubscription()
                .setUserId(userId)
                .setPostId(postId)
                .setSource(StringUtils.hasText(source) ? source : "manual")
                .setCreatedAt(LocalDateTime.now());
        try {
            postSubscriptionMapper.insert(sub);
        } catch (DuplicateKeyException e) {
            // 并发重复订阅：唯一约束兜底，幂等处理
            log.debug("重复订阅已忽略: userId={}, postId={}", userId, postId);
        }
    }

    @Override
    public void unsubscribe(String userId, String postId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(postId)) {
            return;
        }
        postSubscriptionMapper.delete(new LambdaQueryWrapper<PostSubscription>()
                .eq(PostSubscription::getUserId, userId)
                .eq(PostSubscription::getPostId, postId));
    }

    @Override
    public boolean isSubscribed(String userId, String postId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(postId)) {
            return false;
        }
        Long count = postSubscriptionMapper.selectCount(new LambdaQueryWrapper<PostSubscription>()
                .eq(PostSubscription::getUserId, userId)
                .eq(PostSubscription::getPostId, postId));
        return count != null && count > 0;
    }

    @Override
    public List<String> listSubscriberIds(String postId) {
        if (!StringUtils.hasText(postId)) {
            return List.of();
        }
        return postSubscriptionMapper.selectList(new LambdaQueryWrapper<PostSubscription>()
                        .eq(PostSubscription::getPostId, postId))
                .stream()
                .map(PostSubscription::getUserId)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public Page<PostSubscription> listByUser(String userId, int page, int size) {
        Page<PostSubscription> pager = new Page<>(Math.max(1, page), Math.min(Math.max(1, size), 50));
        return postSubscriptionMapper.selectPage(pager, new LambdaQueryWrapper<PostSubscription>()
                .eq(PostSubscription::getUserId, userId)
                .orderByDesc(PostSubscription::getCreatedAt));
    }

    @Override
    public void notifySubscribersOnComment(String postId, String postTitle, String postAuthorId,
                                           String commenterId, Collection<String> excludeUserIds) {
        try {
            List<String> subscriberIds = listSubscriberIds(postId);
            if (subscriberIds.isEmpty()) {
                return;
            }

            Set<String> excludes = new HashSet<>();
            if (excludeUserIds != null) {
                excludes.addAll(excludeUserIds);
            }
            // 评论者本人不通知自己；帖子作者已有专门的评论通知，避免重复
            if (StringUtils.hasText(commenterId)) {
                excludes.add(commenterId);
            }
            if (StringUtils.hasText(postAuthorId)) {
                excludes.add(postAuthorId);
            }

            String safeTitle = StringUtils.hasText(postTitle) ? postTitle : "你追踪的帖子";
            for (String subscriberId : subscriberIds) {
                if (excludes.contains(subscriberId)) {
                    continue;
                }
                try {
                    notificationService.createNotification(
                            subscriberId,
                            NOTIFY_TYPE_POST_ACTIVITY,
                            "你追踪的主题有新回复",
                            "「" + safeTitle + "」有了新的回复，点击查看最新讨论",
                            postId,
                            commenterId
                    );
                } catch (Exception e) {
                    log.warn("追踪通知发送失败: subscriberId={}, postId={}, err={}", subscriberId, postId, e.getMessage());
                }
            }
        } catch (Exception e) {
            // 增强逻辑：任何异常都不阻断评论主流程
            log.error("通知帖子订阅者失败: postId={}", postId, e);
        }
    }
}
