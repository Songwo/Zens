package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trend.campus_pulse.dto.response.NotificationResp;
import com.campus.trend.campus_pulse.entity.Notification;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.NotificationMapper;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import com.campus.trend.campus_pulse.service.MailService;
import com.campus.trend.campus_pulse.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Song：通知服务实现
 */
@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MailService mailService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createNotification(String userId, String type, String title, String content,
            String relatedId, String relatedUserId) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .content(content)
                .relatedId(relatedId)
                .relatedUserId(relatedUserId)
                .isRead(0)
                .createdAt(LocalDateTime.now())
                .build();

        notificationMapper.insert(notification);
        log.info("创建通知: userId={}, type={}", userId, type);

        // Song：说明
        try {
            NotificationResp resp = convertToResp(notification);
            messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", resp);
            log.debug("WebSocket推送通知成功: userId={}, notificationId={}", userId, notification.getId());
        } catch (Exception e) {
            log.error("WebSocket推送通知失败: userId={}", userId, e);
        }

        syncNotificationEmail(userId, title, content);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createNotification(String receiverUserId, String senderUserId,
            String senderName, String senderAvatar,
            String title, String content,
            int typeCode, String relatedPostId) {
        // Song：说明
        String type;
        switch (typeCode) {
            case 1:
                type = "like";
                break;
            case 2:
                type = "reply";
                break;
            case 3:
                type = "favorite";
                break;
            case 4:
                type = "follow";
                break;
            default:
                type = "system";
                break;
        }

        createNotification(receiverUserId, type, title, content, relatedPostId, senderUserId);
    }

    @Override
    public List<NotificationResp> getNotificationList(String userId, int page, int size) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreatedAt);

        Page<Notification> notificationPage = new Page<>(page, size);
        Page<Notification> result = notificationMapper.selectPage(notificationPage, wrapper);

        return result.getRecords().stream()
                .map(this::convertToResp)
                .collect(Collectors.toList());
    }

    @Override
    public long getUnreadCount(String userId) {
        return notificationMapper.countUnreadByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(Long notificationId, String userId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null || !notification.getUserId().equals(userId)) {
            throw new IllegalArgumentException("通知不存在");
        }

        notification.setIsRead(1);
        notificationMapper.updateById(notification);
        log.info("标记通知为已读: notificationId={}", notificationId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllAsRead(String userId) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0);

        List<Notification> notifications = notificationMapper.selectList(wrapper);
        notifications.forEach(n -> {
            n.setIsRead(1);
            notificationMapper.updateById(n);
        });

        log.info("标记所有通知为已读: userId={}, count={}", userId, notifications.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNotification(Long notificationId, String userId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null || !notification.getUserId().equals(userId)) {
            throw new IllegalArgumentException("通知不存在");
        }

        notificationMapper.deleteById(notificationId);
        log.info("删除通知: notificationId={}", notificationId);
    }

    @Override
    public void sendCommentNotification(String postAuthorId, String commenterId, String postId, String content) {
        if (postAuthorId.equals(commenterId)) {
            return; // Song：不给自己发通知
        }

        User commenter = userMapper.selectById(commenterId);
        String title = "新评论";
        String notificationContent = String.format("%s 评论了你的帖子: %s",
                commenter.getNickname(), content.length() > 50 ? content.substring(0, 50) + "..." : content);

        createNotification(postAuthorId, "reply", title, notificationContent, postId, commenterId);
    }

    @Override
    public void sendLikeNotification(String postAuthorId, String likerId, String postId) {
        if (postAuthorId.equals(likerId)) {
            return;
        }

        User liker = userMapper.selectById(likerId);
        String title = "新点赞";
        String content = String.format("%s 点赞了你的帖子", liker.getNickname());

        createNotification(postAuthorId, "like", title, content, postId, likerId);
    }

    @Override
    public void sendFavoriteNotification(String postAuthorId, String favoriteUserId, String postId) {
        if (postAuthorId.equals(favoriteUserId)) {
            return;
        }

        User favoriteUser = userMapper.selectById(favoriteUserId);
        String title = "新收藏";
        String content = String.format("%s 收藏了你的帖子", favoriteUser.getNickname());

        createNotification(postAuthorId, "favorite", title, content, postId, favoriteUserId);
    }

    @Override
    public void sendFollowNotification(String followeeId, String followerId) {
        User follower = userMapper.selectById(followerId);
        String title = "新关注";
        String content = String.format("%s 关注了你", follower.getNickname());

        createNotification(followeeId, "follow", title, content, null, followerId);
    }

    @Override
    public void sendMentionNotification(String mentionedUserId, String mentionerId, String postId, String content) {
        if (mentionedUserId.equals(mentionerId)) {
            return;
        }

        User mentioner = userMapper.selectById(mentionerId);
        String title = "提到了你";
        String notificationContent = String.format("%s 在评论中提到了你: %s",
                mentioner.getNickname(), content.length() > 50 ? content.substring(0, 50) + "..." : content);

        createNotification(mentionedUserId, "mention", title, notificationContent, postId, mentionerId);
    }

    private NotificationResp convertToResp(Notification notification) {
        NotificationResp resp = new NotificationResp();
        BeanUtils.copyProperties(notification, resp);

        // Song：获取关联用户信息
        if (notification.getRelatedUserId() != null) {
            User relatedUser = userMapper.selectById(notification.getRelatedUserId());
            if (relatedUser != null) {
                resp.setRelatedUserNickname(relatedUser.getNickname());
                resp.setRelatedUserAvatar(relatedUser.getAvatar());
            }
        }

        return resp;
    }

    private void syncNotificationEmail(String userId, String title, String content) {
        try {
            User targetUser = userMapper.selectById(userId);
            if (targetUser == null) {
                return;
            }
            if (targetUser.getEmailNotifyEnabled() != null && targetUser.getEmailNotifyEnabled() == 0) {
                return;
            }
            if (!StringUtils.hasText(targetUser.getEmail())) {
                return;
            }
            String nickname = StringUtils.hasText(targetUser.getNickname()) ? targetUser.getNickname() : targetUser.getUsername();
            mailService.sendSimpleMail(
                    targetUser.getEmail(),
                    "【Zens社区】" + (StringUtils.hasText(title) ? title : "你有一条新通知"),
                    "Hi " + nickname + "，你收到一条新通知：\n\n" + content + "\n\n请前往站内查看完整内容。");
        } catch (Exception e) {
            log.warn("通知邮件同步失败: userId={}, err={}", userId, e.getMessage());
        }
    }
}
