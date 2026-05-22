package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trend.campus_pulse.common.notification.NotificationType;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.util.DigestUtils;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${campus.notification.dedupe-window-seconds:120}")
    private long notificationDedupeWindowSeconds;

    private static final String NOTIFICATION_DEDUPE_PREFIX = "notify:dedupe:";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createNotification(String userId, String type, String title, String content,
            String relatedId, String relatedUserId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        if (shouldSkipDuplicateNotification(userId, type, title, content, relatedId, relatedUserId)) {
            log.debug("通知命中去重窗口，跳过投递: userId={}, type={}, relatedId={}", userId, type, relatedId);
            return;
        }
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

        try {
            NotificationResp resp = convertToResp(notification, loadRelatedUserMap(List.of(notification)));
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
        Map<String, User> relatedUserMap = loadRelatedUserMap(result.getRecords());

        return result.getRecords().stream()
                .map(notification -> convertToResp(notification, relatedUserMap))
                .collect(Collectors.toList());
    }

    @Override
    public long getUnreadCount(String userId) {
        return notificationMapper.countUnreadByUserId(userId);
    }

    @Override
    public long countByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return 0L;
        }
        Long count = notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>().eq(Notification::getUserId, userId));
        return count == null ? 0L : count;
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
        LambdaUpdateWrapper<Notification> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1);
        int updated = notificationMapper.update(null, updateWrapper);
        log.info("标记所有通知为已读: userId={}, count={}", userId, updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markBatchAsRead(List<Long> notificationIds, String userId) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            return;
        }
        List<Long> sanitizedIds = notificationIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
        if (sanitizedIds.isEmpty()) {
            return;
        }

        LambdaUpdateWrapper<Notification> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .in(Notification::getId, sanitizedIds)
                .set(Notification::getIsRead, 1);
        int updated = notificationMapper.update(null, updateWrapper);
        log.info("批量标记通知已读: userId={}, requested={}, updated={}", userId, sanitizedIds.size(), updated);
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
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(List<Long> notificationIds, String userId) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            return;
        }
        List<Long> sanitizedIds = notificationIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
        if (sanitizedIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<Notification> ownedWrapper = new LambdaQueryWrapper<>();
        ownedWrapper.eq(Notification::getUserId, userId)
                .in(Notification::getId, sanitizedIds)
                .select(Notification::getId);
        Set<Long> ownedIds = notificationMapper.selectList(ownedWrapper).stream()
                .map(Notification::getId)
                .collect(Collectors.toSet());
        if (ownedIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<Notification> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.in(Notification::getId, ownedIds);
        int deleted = notificationMapper.delete(deleteWrapper);
        log.info("批量删除通知: userId={}, requested={}, deleted={}", userId, sanitizedIds.size(), deleted);
    }

    @Override
    public void sendCommentNotification(String postAuthorId, String commenterId, String postId, String commentId, String content) {
        if (!StringUtils.hasText(postAuthorId)) {
            return; // Song：不给自己发通知
        }
        if (StringUtils.hasText(commenterId) && postAuthorId.equals(commenterId)) {
            return;
        }

        User commenter = userMapper.selectById(commenterId);
        String commenterName = (commenter != null && StringUtils.hasText(commenter.getNickname()))
                ? commenter.getNickname()
                : "匿名用户";
        String excerpt = StringUtils.hasText(content) ? content : "";
        String title = "新评论";
        String notificationContent = String.format("%s 评论了你的帖子: %s",
                commenterName, excerpt.length() > 50 ? excerpt.substring(0, 50) + "..." : excerpt);

        createNotification(postAuthorId, "reply", title, notificationContent, buildPostRelatedId(postId, commentId), commenter != null ? commenterId : null);
    }

    @Override
    public void sendLikeNotification(String postAuthorId, String likerId, String postId) {
        if (!StringUtils.hasText(postAuthorId) || !StringUtils.hasText(likerId)) {
            return;
        }
        if (postAuthorId.equals(likerId)) {
            return;
        }

        User liker = userMapper.selectById(likerId);
        String likerName = (liker != null && StringUtils.hasText(liker.getNickname()))
                ? liker.getNickname() : "匿名用户";
        String title = "新点赞";
        String content = String.format("%s 点赞了你的帖子", likerName);

        createNotification(postAuthorId, "like", title, content, postId, likerId);
    }

    @Override
    public void sendFavoriteNotification(String postAuthorId, String favoriteUserId, String postId) {
        if (!StringUtils.hasText(postAuthorId) || !StringUtils.hasText(favoriteUserId)) {
            return;
        }
        if (postAuthorId.equals(favoriteUserId)) {
            return;
        }

        User favoriteUser = userMapper.selectById(favoriteUserId);
        String userName = (favoriteUser != null && StringUtils.hasText(favoriteUser.getNickname()))
                ? favoriteUser.getNickname() : "匿名用户";
        String title = "新收藏";
        String content = String.format("%s 收藏了你的帖子", userName);

        createNotification(postAuthorId, "favorite", title, content, postId, favoriteUserId);
    }

    @Override
    public void sendFollowNotification(String followeeId, String followerId) {
        if (!StringUtils.hasText(followeeId) || !StringUtils.hasText(followerId)) {
            return;
        }
        if (followeeId.equals(followerId)) {
            return;
        }
        User follower = userMapper.selectById(followerId);
        String followerName = (follower != null && StringUtils.hasText(follower.getNickname()))
                ? follower.getNickname() : "匿名用户";
        String title = "新关注";
        String content = String.format("%s 关注了你", followerName);

        createNotification(followeeId, "follow", title, content, null, followerId);
    }

    @Override
    public void sendMentionNotification(String mentionedUserId, String mentionerId, String postId, String commentId, String content) {
        if (!StringUtils.hasText(mentionedUserId)) {
            return;
        }
        if (StringUtils.hasText(mentionerId) && mentionedUserId.equals(mentionerId)) {
            return;
        }

        User mentioner = userMapper.selectById(mentionerId);
        String mentionerName = (mentioner != null && StringUtils.hasText(mentioner.getNickname()))
                ? mentioner.getNickname()
                : "匿名用户";
        String excerpt = StringUtils.hasText(content) ? content : "";
        String title = "提到了你";
        String notificationContent = String.format("%s 在评论中提到了你: %s",
                mentionerName, excerpt.length() > 50 ? excerpt.substring(0, 50) + "..." : excerpt);

        createNotification(mentionedUserId, "mention", title, notificationContent, buildPostRelatedId(postId, commentId), mentioner != null ? mentionerId : null);
    }

    private NotificationResp convertToResp(Notification notification) {
        return convertToResp(notification, loadRelatedUserMap(List.of(notification)));
    }

    private NotificationResp convertToResp(Notification notification, Map<String, User> relatedUserMap) {
        NotificationResp resp = new NotificationResp();
        BeanUtils.copyProperties(notification, resp);
        resp.setCategory(NotificationType.categoryOf(notification.getType()).name());

        if (notification.getRelatedUserId() != null) {
            User relatedUser = relatedUserMap.get(notification.getRelatedUserId());
            if (relatedUser != null) {
                resp.setRelatedUserNickname(relatedUser.getNickname());
                resp.setRelatedUserAvatar(relatedUser.getAvatar());
            }
        }

        return resp;
    }

    private Map<String, User> loadRelatedUserMap(List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<String> relatedUserIds = notifications.stream()
                .map(Notification::getRelatedUserId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (relatedUserIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, User> relatedUserMap = new HashMap<>();
        userMapper.selectBatchIds(relatedUserIds)
                .forEach(user -> relatedUserMap.put(user.getId(), user));
        return relatedUserMap;
    }

    private boolean shouldSkipDuplicateNotification(String userId,
                                                    String type,
                                                    String title,
                                                    String content,
                                                    String relatedId,
                                                    String relatedUserId) {
        String bucketKey = NOTIFICATION_DEDUPE_PREFIX
                + userId + ":"
                + (StringUtils.hasText(type) ? type : "system") + ":"
                + (StringUtils.hasText(relatedId) ? relatedId : "-");
        String fingerprintSource = String.join("|",
                normalizeDedupeValue(type),
                normalizeDedupeValue(title),
                normalizeDedupeValue(fingerprintContent(type, content)),
                normalizeDedupeValue(relatedId),
                normalizeDedupeValue(relatedUserId));
        String fingerprint = DigestUtils.md5DigestAsHex(fingerprintSource.getBytes(StandardCharsets.UTF_8));
        try {
            Long added = stringRedisTemplate.opsForSet().add(bucketKey, fingerprint);
            long windowSeconds = dedupeWindowFor(type);
            stringRedisTemplate.expire(bucketKey, Duration.ofSeconds(windowSeconds));
            return added == null || added == 0L;
        } catch (Exception e) {
            log.debug("通知去重失败，继续投递: userId={}, err={}", userId, e.getMessage());
            return false;
        }
    }

    /** 登录/会话类安全提醒使用更长窗口，避免同一登录事件生成多条站内信。 */
    private long dedupeWindowFor(String type) {
        long base = Math.max(30L, notificationDedupeWindowSeconds);
        if (NotificationType.NEW_DEVICE_LOGIN.getCode().equals(type)
                || NotificationType.SESSION_TERMINATED.getCode().equals(type)
                || NotificationType.LOGIN_FAILED_BURST.getCode().equals(type)) {
            return Math.max(base, 3600L);
        }
        return NotificationType.categoryOf(type) == NotificationType.NotificationCategory.SECURITY
                ? Math.max(base, 300L)
                : base;
    }

    private String normalizeDedupeValue(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    private String fingerprintContent(String type, String content) {
        if (NotificationType.categoryOf(type) == NotificationType.NotificationCategory.SECURITY) {
            return "-";
        }
        return content;
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

    private String buildPostRelatedId(String postId, String commentId) {
        if (!StringUtils.hasText(postId)) {
            return null;
        }
        if (!StringUtils.hasText(commentId)) {
            return postId;
        }
        return postId + "#comment-" + commentId;
    }

    // ==================== 安全事件实现 ====================

    @Override
    public void sendNewDeviceLoginNotification(String userId, String deviceType, String clientIp, String userAgent) {
        if (!StringUtils.hasText(userId)) return;
        String title = "新设备登录提醒";
        String content = String.format(
                "你的账号刚在新的%s设备上登录，IP：%s，时间：%s。如非本人操作，请立即修改密码并开启二步验证。",
                describeDeviceType(deviceType),
                defaultString(clientIp, "未知"),
                LocalDateTime.now().format(SECURITY_TIME_FMT));
        if (StringUtils.hasText(userAgent)) {
            content += "\n浏览器：" + userAgent;
        }
        createNotification(userId, NotificationType.NEW_DEVICE_LOGIN, title, content, null, null);
    }

    @Override
    public void sendSessionTerminatedNotification(String userId, String deviceType, String clientIp) {
        if (!StringUtils.hasText(userId)) return;
        String content = String.format(
                "你的账号已在另一%s设备登录，此设备已被自动登出。IP：%s，时间：%s。",
                describeDeviceType(deviceType),
                defaultString(clientIp, "未知"),
                LocalDateTime.now().format(SECURITY_TIME_FMT));
        createNotification(userId, NotificationType.SESSION_TERMINATED, "会话已被强制下线", content, null, null);
    }

    @Override
    public void sendPasswordChangedNotification(String userId, String clientIp) {
        if (!StringUtils.hasText(userId)) return;
        String content = String.format(
                "你的登录密码已修改成功。IP：%s，时间：%s。如非本人操作，请立即联系管理员并使用找回密码功能重置。",
                defaultString(clientIp, "未知"),
                LocalDateTime.now().format(SECURITY_TIME_FMT));
        createNotification(userId, NotificationType.PASSWORD_CHANGED, "密码修改成功", content, null, null);
    }

    @Override
    public void sendPasswordResetNotification(String userId, String clientIp) {
        if (!StringUtils.hasText(userId)) return;
        String content = String.format(
                "你的账号已通过邮箱验证码重置密码。IP：%s，时间：%s。如非本人操作，请立即联系管理员。",
                defaultString(clientIp, "未知"),
                LocalDateTime.now().format(SECURITY_TIME_FMT));
        createNotification(userId, NotificationType.PASSWORD_RESET, "密码已重置", content, null, null);
    }

    @Override
    public void sendTwoFactorToggleNotification(String userId, boolean enabled) {
        if (!StringUtils.hasText(userId)) return;
        String title = enabled ? "二步验证已开启" : "二步验证已关闭";
        String content = enabled
                ? "你已成功为账号开启二步验证，后续登录需要输入验证器 APP 中的动态码。时间：" + LocalDateTime.now().format(SECURITY_TIME_FMT)
                : "你已关闭账号的二步验证，账号安全性下降。时间：" + LocalDateTime.now().format(SECURITY_TIME_FMT) + "。如非本人操作，请立即修改密码并重新开启。";
        createNotification(userId,
                enabled ? NotificationType.TWO_FACTOR_ENABLED : NotificationType.TWO_FACTOR_DISABLED,
                title, content, null, null);
    }

    @Override
    public void sendLoginFailedBurstNotification(String userId, int failedAttempts, String clientIp) {
        if (!StringUtils.hasText(userId)) return;
        String content = String.format(
                "过去 10 分钟内检测到 %d 次登录失败尝试，IP：%s，时间：%s。如非本人操作，建议立即修改密码并开启二步验证。",
                failedAttempts,
                defaultString(clientIp, "未知"),
                LocalDateTime.now().format(SECURITY_TIME_FMT));
        createNotification(userId, NotificationType.LOGIN_FAILED_BURST, "登录失败次数异常", content, null, null);
    }

    private static final java.time.format.DateTimeFormatter SECURITY_TIME_FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static String describeDeviceType(String deviceType) {
        if (!StringUtils.hasText(deviceType)) return "未知";
        return switch (deviceType.toLowerCase()) {
            case "pc", "desktop" -> "电脑";
            case "mobile", "mob" -> "移动";
            default -> deviceType;
        };
    }

    private static String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
