package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.dto.request.CommentCreateReq;
import com.campus.trend.campus_pulse.dto.response.CommentResp;
import com.campus.trend.campus_pulse.entity.Comment;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.CommentMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.*;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    private static final String POST_FEED_CACHE_GLOBAL_VERSION_KEY = "post:feed:version:global";
    private static final String POST_FEED_CACHE_SECTION_VERSION_KEY_PREFIX = "post:feed:version:section:";

    private final PostMapper sysPostMapper;
    private final ContentSecurityService contentSecurityService;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    private final UserService userService;
    private final NotificationService notificationService;
    private final com.campus.trend.campus_pulse.service.PostEventService postEventService;
    private final com.campus.trend.campus_pulse.service.LevelService levelService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addComment(CommentCreateReq request, String userId) {
        Comment comment = new Comment();
        comment.setPostId(request.getPostId());
        comment.setUserId(userId == null ? "0" : userId);

        String content = request.getContent();
        if (contentSecurityService.containsSensitiveWords(content)) {
            content = contentSecurityService.filterSensitiveWords(content);
        }
        comment.setContent(content);

        comment.setParentId(request.getParentId());
        comment.setReplyUserId(request.getReplyUserId());
        comment.setIsAnonymous(request.getIsAnonymous());
        comment.setCreateTime(LocalDateTime.now());
        comment.setLikeCount(0);
        this.save(comment);

        Post post = sysPostMapper.selectById(comment.getPostId());
        if (post != null) {
            post.setCommentCount(post.getCommentCount() + 1);
            // Song：更新最后回复时间和最后活跃时间
            LocalDateTime now = LocalDateTime.now();
            post.setLastReplyAt(now);
            post.setLastActivityAt(now);
            sysPostMapper.updateById(post);
            invalidatePostFeedCache(post.getSectionId());

            // Song：推送新回复事件
            try {
                postEventService.pushPostReplied(post.getId(), post.getSectionId());
            } catch (Exception e) {
                // Song：静默失败，不影响主流程
            }

            // Song：说明
            // Song：说明
            if (!post.getUserId().equals(userId)) {
                notificationService.sendCommentNotification(
                    post.getUserId(),
                    userId,
                    post.getId(),
                    content
                );

                // Song：被评论经验 +3（给帖子作者）
                levelService.addExperience(post.getUserId(), 3, "被评论");
            }
        }

        // Song：说明
        if (request.getParentId() != null && !"0".equals(request.getParentId())) {
            Comment parentComment = this.getById(request.getParentId());
            if (parentComment != null && !parentComment.getUserId().equals(userId)) {
                notificationService.sendMentionNotification(
                    parentComment.getUserId(),
                    userId,
                    post != null ? post.getId() : null,
                    content
                );
            }
        }

        if (userId != null) {
            userService.addContribution(userId, 2);
            userService.updateLastActiveTime(userId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(String commentId, String userId) {
        Comment comment = getById(commentId);
        if (comment == null) {
            throw new RuntimeException("评论不存在");
        }

        if (!comment.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除该评论");
        }

        removeById(commentId);

        Post post = sysPostMapper.selectById(comment.getPostId());
        if (post != null) {
            post.setCommentCount(Math.max(0, post.getCommentCount() - 1));
            sysPostMapper.updateById(post);
            invalidatePostFeedCache(post.getSectionId());
        }
    }

    @Override
    public com.baomidou.mybatisplus.core.metadata.IPage<CommentResp> getCommentsByPostId(String postId,
            Integer pageNo, Integer pageSize) {
        Page<Comment> page = new Page<>(pageNo, pageSize);
        Page<Comment> rootPage = this.page(page, Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getPostId, postId)
                .eq(Comment::getParentId, "0")
                .orderByDesc(Comment::getCreateTime));

        List<Comment> roots = rootPage.getRecords();
        if (roots.isEmpty()) {
            return new Page<CommentResp>(pageNo, pageSize).setRecords(Collections.emptyList());
        }

        List<CommentResp> responseList = roots.stream().map(root -> {
            fillChildren(root);
            return mapToResponse(root);
        }).collect(Collectors.toList());

        fillUserInfo(responseList);

        Page<CommentResp> resultPage = new Page<>(pageNo, pageSize);
        resultPage.setTotal(rootPage.getTotal());
        resultPage.setPages(rootPage.getPages());
        resultPage.setCurrent(rootPage.getCurrent());
        resultPage.setRecords(responseList);

        try {
            AuthUser authUser = SecurityUtils.getAuthenticatedUser();
            if (authUser != null) {
                String currentUserId = authUser.getUser().getId();
                fillLikeStatus(responseList, currentUserId);
            }
        } catch (Exception ignored) {
            // Song：说明
        }

        return resultPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleLike(String commentId, String userId) {
        Comment comment = getById(commentId);
        if (comment == null) {
            throw new RuntimeException("评论不存在");
        }

        String key = "comment:like:" + commentId + ":" + userId;
        Boolean hasLiked = stringRedisTemplate.hasKey(key);

        if (Boolean.TRUE.equals(hasLiked)) {
            stringRedisTemplate.delete(key);
            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
            userService.decrementLikesReceived(comment.getUserId());
        } else {
            stringRedisTemplate.opsForValue().set(key, "1");
            comment.setLikeCount(comment.getLikeCount() + 1);
            userService.incrementLikesReceived(comment.getUserId());
        }
        updateById(comment);
    }

    private void fillChildren(Comment parent) {
        List<Comment> children = this.list(Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getParentId, parent.getId())
                .orderByAsc(Comment::getCreateTime));

        if (children != null && !children.isEmpty()) {
            parent.setChildren(children);
            children.forEach(this::fillChildren);
        }
    }

    private CommentResp mapToResponse(Comment comment) {
        CommentResp response = new CommentResp();
        BeanUtils.copyProperties(comment, response);

        if (comment.getChildren() != null && !comment.getChildren().isEmpty()) {
            List<CommentResp> childrenDtos = comment.getChildren().stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
            response.setChildren(childrenDtos);
        }
        return response;
    }

    private void fillUserInfo(List<CommentResp> responses) {
        if (responses == null || responses.isEmpty())
            return;

        Set<String> userIds = new HashSet<>();
        collectUserIds(responses, userIds);

        if (userIds.isEmpty())
            return;

        List<User> users = userService.listByIds(userIds);
        Map<String, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        populateInfo(responses, userMap);
    }

    private void collectUserIds(List<CommentResp> responses, Set<String> userIds) {
        for (CommentResp c : responses) {
            if (c.getUserId() != null && !"0".equals(c.getUserId())) {
                userIds.add(c.getUserId());
            }
            if (c.getReplyUserId() != null && !"0".equals(c.getReplyUserId())) {
                userIds.add(c.getReplyUserId());
            }
            if (c.getChildren() != null) {
                collectUserIds(c.getChildren(), userIds);
            }
        }
    }

    private void populateInfo(List<CommentResp> responses, Map<String, User> userMap) {
        for (CommentResp c : responses) {
            if (c.getIsAnonymous() != null && c.getIsAnonymous() == 1) {
                // Song：说明
            } else {
                User user = userMap.get(c.getUserId());
                if (user != null) {
                    c.setNickname(user.getNickname());
                    c.setUserAvatar(user.getAvatar());

                    String role = user.getRole() != null ? user.getRole() : "ROLE_USER";
                    c.setRoles(List.of(role));
                }
            }

            if (c.getReplyUserId() != null) {
                User replyUser = userMap.get(c.getReplyUserId());
                if (replyUser != null) {
                    c.setReplyUserNickname(replyUser.getNickname());
                }
            }

            if (c.getChildren() != null) {
                populateInfo(c.getChildren(), userMap);
            }
        }
    }

    private void fillLikeStatus(List<CommentResp> responses, String currentUserId) {
        for (CommentResp c : responses) {
            String key = "comment:like:" + c.getId() + ":" + currentUserId;
            c.setIsLiked(stringRedisTemplate.hasKey(key));
            if (c.getChildren() != null) {
                fillLikeStatus(c.getChildren(), currentUserId);
            }
        }
    }

    private void invalidatePostFeedCache(Long sectionId) {
        try {
            stringRedisTemplate.opsForValue().increment(POST_FEED_CACHE_GLOBAL_VERSION_KEY);
            if (sectionId != null) {
                stringRedisTemplate.opsForValue().increment(POST_FEED_CACHE_SECTION_VERSION_KEY_PREFIX + sectionId);
            }
        } catch (Exception ignored) {
            // Song：不影响评论主流程
        }
    }
}
