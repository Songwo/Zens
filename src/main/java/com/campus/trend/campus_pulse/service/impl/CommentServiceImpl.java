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

    private final PostMapper sysPostMapper;
    private final UserProfileService userProfileService;
    private final ContentSecurityService contentSecurityService;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    private final UserService userService;
    private final SysNotificationService notificationService;

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
            sysPostMapper.updateById(post);
            
            // Notification Logic
            // 1. Notify Post Author
            if (!post.getUserId().equals(userId)) {
                 User sender = userService.getById(userId);
                 String senderName = (request.getIsAnonymous() != null && request.getIsAnonymous() == 1) ? "匿名用户" : (sender != null ? sender.getNickname() : "有人");
                 String senderAvatar = (request.getIsAnonymous() != null && request.getIsAnonymous() == 1) ? null : (sender != null ? sender.getAvatar() : null);
                 
                 notificationService.createNotification(
                     post.getUserId(),
                     userId,
                     senderName,
                     senderAvatar,
                     "收到新评论",
                     "评论了你的动态: " + post.getTitle(),
                     2, // Reply
                     post.getId()
                 );
            }
        }
        
        // 2. Notify Reply Target
        if (request.getReplyUserId() != null && !request.getReplyUserId().equals(userId) && (post == null || !request.getReplyUserId().equals(post.getUserId()))) {
             // Only notify if not already notified as post author
             User sender = userService.getById(userId);
             String senderName = (request.getIsAnonymous() != null && request.getIsAnonymous() == 1) ? "匿名用户" : (sender != null ? sender.getNickname() : "有人");
             String senderAvatar = (request.getIsAnonymous() != null && request.getIsAnonymous() == 1) ? null : (sender != null ? sender.getAvatar() : null);

             notificationService.createNotification(
                 request.getReplyUserId(),
                 userId,
                 senderName,
                 senderAvatar,
                 "收到新回复",
                 "回复了你的评论: " + content,
                 2, // Reply
                 post != null ? post.getId() : null
             );
        }

        if (userId != null) {
            userProfileService.addContribution(userId, 2);
            userProfileService.updateLastActiveTime(userId);
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
            // Unauthenticated user
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
            userProfileService.decrementLikesReceived(comment.getUserId());
        } else {
            stringRedisTemplate.opsForValue().set(key, "1");
            comment.setLikeCount(comment.getLikeCount() + 1);
            userProfileService.incrementLikesReceived(comment.getUserId());
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
                // Keep anonymous
            } else {
                User user = userMap.get(c.getUserId());
                if (user != null) {
                    c.setNickname(user.getNickname());
                    c.setUserAvatar(user.getAvatar());
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
}
