package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.dto.event.PostEvent;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.service.PostEventService;
import com.campus.trend.campus_pulse.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Song：帖子事件推送服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostEventServiceImpl implements PostEventService {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;

    @Override
    public void pushPostCreated(Post post) {
        try {
            User author = userService.getById(post.getUserId());
            String authorName = post.getIsAnonymous() == 1 ? "匿名同学"
                : (author != null ? author.getNickname() : "未知用户");
            String authorAvatar = post.getIsAnonymous() == 1 ? null
                : (author != null ? author.getAvatar() : null);

            PostEvent event = PostEvent.builder()
                .type(PostEvent.EventType.POST_CREATED)
                .postId(post.getId())
                .sectionId(post.getSectionId())
                .title(post.getTitle())
                .authorName(authorName)
                .authorAvatar(authorAvatar)
                .timestamp(LocalDateTime.now())
                .build();

            pushEvent(event);
        } catch (Exception e) {
            log.error("推送新帖创建事件失败: postId={}", post.getId(), e);
        }
    }

    @Override
    public void pushPostReplied(String postId, Long sectionId) {
        pushPostReplied(postId, sectionId, null);
    }

    @Override
    public void pushPostReplied(String postId, Long sectionId, Integer commentCount) {
        try {
            PostEvent.UpdateData data = commentCount != null
                ? PostEvent.UpdateData.builder().commentCount(commentCount).build()
                : null;
            PostEvent event = PostEvent.builder()
                .type(PostEvent.EventType.POST_REPLIED)
                .postId(postId)
                .sectionId(sectionId)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
            pushEvent(event);
        } catch (Exception e) {
            log.error("推送新回复事件失败: postId={}", postId, e);
        }
    }

    @Override
    public void pushPostViewed(String postId, Long sectionId, Integer viewCount, LocalDateTime lastActivityAt) {
        try {
            PostEvent.UpdateData data = PostEvent.UpdateData.builder()
                .viewCount(viewCount)
                .lastActivityAt(lastActivityAt)
                .build();

            PostEvent event = PostEvent.builder()
                .type(PostEvent.EventType.POST_VIEWED)
                .postId(postId)
                .sectionId(sectionId)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();

            pushEvent(event);
        } catch (Exception e) {
            log.error("推送浏览量更新事件失败: postId={}", postId, e);
        }
    }

    @Override
    public void pushPostLiked(String postId, Long sectionId, Integer likeCount) {
        try {
            PostEvent.UpdateData data = PostEvent.UpdateData.builder()
                .likeCount(likeCount)
                .build();

            PostEvent event = PostEvent.builder()
                .type(PostEvent.EventType.POST_LIKED)
                .postId(postId)
                .sectionId(sectionId)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();

            pushEvent(event);
        } catch (Exception e) {
            log.error("推送点赞数更新事件失败: postId={}", postId, e);
        }
    }

    @Override
    public void pushPostCollected(String postId, Long sectionId, Integer collectCount) {
        try {
            PostEvent.UpdateData data = PostEvent.UpdateData.builder()
                .collectCount(collectCount)
                .build();

            PostEvent event = PostEvent.builder()
                .type(PostEvent.EventType.POST_COLLECTED)
                .postId(postId)
                .sectionId(sectionId)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();

            pushEvent(event);
        } catch (Exception e) {
            log.error("推送收藏数更新事件失败: postId={}", postId, e);
        }
    }

    @Override
    public void pushPinUpdated(Post post) {
        try {
            PostEvent.UpdateData data = PostEvent.UpdateData.builder()
                .globalPin(post.getGlobalPin())
                .categoryPin(post.getCategoryPin())
                .pinOrder(post.getPinOrder())
                .build();

            PostEvent event = PostEvent.builder()
                .type(PostEvent.EventType.PIN_UPDATED)
                .postId(post.getId())
                .sectionId(post.getSectionId())
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();

            pushEvent(event);
        } catch (Exception e) {
            log.error("推送置顶状态更新事件失败: postId={}", post.getId(), e);
        }
    }

    @Override
    public void pushEvent(PostEvent event) {
        try {
            // Song：推送到全局频道（所有用户）
            messagingTemplate.convertAndSend("/topic/posts", event);

            if (event.getSectionId() != null) {
                messagingTemplate.convertAndSend("/topic/section/" + event.getSectionId(), event);
            }

            log.debug("推送事件成功: type={}, postId={}", event.getType(), event.getPostId());
        } catch (Exception e) {
            log.error("推送事件失败: {}", event, e);
        }
    }
}
