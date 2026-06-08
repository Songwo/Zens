package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.UserTagRelation;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import com.campus.trend.campus_pulse.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTaskServiceImpl implements AsyncTaskService {

    private final TagService tagService;
    private final LevelService levelService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final PostEventService postEventService;
    private final UserTagRelationService userTagRelationService;
    private final UserMapper userMapper;
    private final MailService mailService;

    @Override
    @Async("taskExecutor")
    public void processPostTagsAsync(String tagsString) {
        if (!StringUtils.hasText(tagsString)) return;
        try {
            String[] tagNames = tagsString.split("[,，\\s#]+");
            for (String tagName : tagNames) {
                String t = tagName.trim();
                if (!t.isEmpty()) {
                    try {
                        com.campus.trend.campus_pulse.entity.Tag tag = tagService.getOrCreateTag(t);
                        tagService.increaseHeat(tag.getId());
                    } catch (Exception e) {
                        log.warn("processPostTagsAsync tag={} err={}", t, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("processPostTagsAsync err={}", e.getMessage());
        }
    }

    @Override
    @Async("notifyExecutor")
    public void notifyTagFollowersAsync(String authorUserId, String postId, String postTitle, String tagsString) {
        if (!StringUtils.hasText(tagsString)) return;
        try {
            String[] tagNames = tagsString.split("[,，\\s#]+");
            for (String rawTag : tagNames) {
                String t = rawTag.trim();
                if (t.isEmpty()) continue;
                try {
                    com.campus.trend.campus_pulse.entity.Tag tag = tagService.lambdaQuery()
                            .eq(com.campus.trend.campus_pulse.entity.Tag::getName, t).one();
                    if (tag == null) continue;
                    List<String> followerIds = userTagRelationService.lambdaQuery()
                            .eq(UserTagRelation::getTagId, tag.getId())
                            .list().stream()
                            .map(r -> String.valueOf(r.getUserId()))
                            .collect(Collectors.toList());
                    for (String fid : followerIds) {
                        if (fid.equals(authorUserId)) continue;
                        try {
                            notificationService.createNotification(fid, "tag_post",
                                    "关注的话题有新帖",
                                    "话题 #" + t + " 有新帖：" + postTitle,
                                    postId, authorUserId);
                        } catch (Exception ex) {
                            log.warn("notifyTagFollower fid={} err={}", fid, ex.getMessage());
                        }
                    }
                } catch (Exception e) {
                    log.warn("notifyTagFollowersAsync tag={} err={}", t, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("notifyTagFollowersAsync err={}", e.getMessage());
        }
    }

    @Override
    @Async("taskExecutor")
    public void addExperienceAsync(String userId, int exp, String reason) {
        try { levelService.addExperience(userId, exp, reason); }
        catch (Exception e) { log.warn("addExperienceAsync uid={} err={}", userId, e.getMessage()); }
    }

    @Override
    @Async("taskExecutor")
    public void updateLastActiveAsync(String userId) {
        try { userService.updateLastActiveTime(userId); }
        catch (Exception e) { log.warn("updateLastActiveAsync uid={} err={}", userId, e.getMessage()); }
    }

    @Override
    @Async("notifyExecutor")
    public void sendSystemNotificationAsync(String userId, String title, String content, Object relatedId) {
        try { notificationService.createNotification(userId, "system", title, content, relatedId != null ? String.valueOf(relatedId) : null, null); }
        catch (Exception e) { log.warn("sendSystemNotificationAsync uid={} err={}", userId, e.getMessage()); }
    }

    @Override
    @Async("notifyExecutor")
    public void pushPostRepliedAsync(String postId, Long sectionId, Integer commentCount) {
        try { postEventService.pushPostReplied(postId, sectionId, commentCount); }
        catch (Exception e) { log.warn("pushPostRepliedAsync err={}", e.getMessage()); }
    }

    @Override
    @Async("notifyExecutor")
    public void pushPostCreatedAsync(Post post) {
        try { postEventService.pushPostCreated(post); }
        catch (Exception e) { log.warn("pushPostCreatedAsync err={}", e.getMessage()); }
    }

    @Override
    @Async("notifyExecutor")
    public void pushPostLikedAsync(String postId, Long sectionId, Integer likeCount) {
        try { postEventService.pushPostLiked(postId, sectionId, likeCount); }
        catch (Exception e) { log.warn("pushPostLikedAsync err={}", e.getMessage()); }
    }

    @Override
    @Async("notifyExecutor")
    public void pushPostCollectedAsync(String postId, Long sectionId, Integer collectCount) {
        try { postEventService.pushPostCollected(postId, sectionId, collectCount); }
        catch (Exception e) { log.warn("pushPostCollectedAsync err={}", e.getMessage()); }
    }

    @Override
    @Async("taskExecutor")
    public void updateActiveRegionAsync(String userId, String region) {
        try { userService.updateActiveRegion(userId, region); }
        catch (Exception e) { log.warn("updateActiveRegionAsync uid={} err={}", userId, e.getMessage()); }
    }

    @Override
    @Async("notifyExecutor")
    public void syncNotificationEmailAsync(String userId, String title, String content) {
        try {
            com.campus.trend.campus_pulse.entity.User targetUser = userMapper.selectById(userId);
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
            log.warn("异步通知邮件同步失败: userId={}, err={}", userId, e.getMessage());
        }
    }
}
