package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.dto.request.CreateCommentRequest;
import com.campus.trend.campus_pulse.dto.response.CommentResponse;
import com.campus.trend.campus_pulse.entity.SysComment;
import com.campus.trend.campus_pulse.entity.SysPost;
import com.campus.trend.campus_pulse.entity.SysUser;
import com.campus.trend.campus_pulse.mapper.SysCommentMapper;
import com.campus.trend.campus_pulse.mapper.SysPostMapper;
import com.campus.trend.campus_pulse.security.AuthSysUser;
import com.campus.trend.campus_pulse.service.CommentService;
import com.campus.trend.campus_pulse.service.ContentSecurityService;
import com.campus.trend.campus_pulse.service.UserProfileService;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.utils.GetUserDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<SysCommentMapper, SysComment> implements CommentService {

    private final SysPostMapper sysPostMapper;
    private final UserProfileService userProfileService;
    private final ContentSecurityService contentSecurityService;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    private final UserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addComment(CreateCommentRequest request, String userId) {
        SysComment comment = new SysComment();
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

        SysPost post = sysPostMapper.selectById(comment.getPostId());
        if (post != null) {
            post.setCommentCount(post.getCommentCount() + 1);
            sysPostMapper.updateById(post);
        }

        if (userId != null) {
            userProfileService.addContribution(userId, 2);
            userProfileService.updateLastActiveTime(userId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(String commentId, String userId) {
        SysComment comment = getById(commentId);
        if (comment == null) {
            throw new RuntimeException("评论不存在");
        }

        if (!comment.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除该评论");
        }

        removeById(commentId);

        SysPost post = sysPostMapper.selectById(comment.getPostId());
        if (post != null) {
            post.setCommentCount(Math.max(0, post.getCommentCount() - 1));
            sysPostMapper.updateById(post);
        }
    }

    @Override
    public com.baomidou.mybatisplus.core.metadata.IPage<CommentResponse> getCommentsByPostId(String postId,
            Integer pageNo, Integer pageSize) {
        Page<SysComment> page = new Page<>(pageNo, pageSize);
        Page<SysComment> rootPage = this.page(page, Wrappers.<SysComment>lambdaQuery()
                .eq(SysComment::getPostId, postId)
                .eq(SysComment::getParentId, "0")
                .orderByDesc(SysComment::getCreateTime));

        List<SysComment> roots = rootPage.getRecords();
        if (roots.isEmpty()) {
            return new Page<CommentResponse>(pageNo, pageSize).setRecords(Collections.emptyList());
        }

        List<CommentResponse> responseList = roots.stream().map(root -> {
            fillChildren(root);
            return mapToResponse(root);
        }).collect(Collectors.toList());

        fillUserInfo(responseList);

        Page<CommentResponse> resultPage = new Page<>(pageNo, pageSize);
        resultPage.setTotal(rootPage.getTotal());
        resultPage.setPages(rootPage.getPages());
        resultPage.setCurrent(rootPage.getCurrent());
        resultPage.setRecords(responseList);

        try {
            AuthSysUser authSysUser = GetUserDetail.getAuthenticatedUser();
            if (authSysUser != null) {
                String currentUserId = authSysUser.getSysUser().getId();
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
        SysComment comment = getById(commentId);
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

    private void fillChildren(SysComment parent) {
        List<SysComment> children = this.list(Wrappers.<SysComment>lambdaQuery()
                .eq(SysComment::getParentId, parent.getId())
                .orderByAsc(SysComment::getCreateTime));

        if (children != null && !children.isEmpty()) {
            parent.setChildren(children);
            children.forEach(this::fillChildren);
        }
    }

    private CommentResponse mapToResponse(SysComment comment) {
        CommentResponse response = new CommentResponse();
        BeanUtils.copyProperties(comment, response);

        if (comment.getChildren() != null && !comment.getChildren().isEmpty()) {
            List<CommentResponse> childrenDtos = comment.getChildren().stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
            response.setChildren(childrenDtos);
        }
        return response;
    }

    private void fillUserInfo(List<CommentResponse> responses) {
        if (responses == null || responses.isEmpty())
            return;

        Set<String> userIds = new HashSet<>();
        collectUserIds(responses, userIds);

        if (userIds.isEmpty())
            return;

        List<SysUser> users = userService.listByIds(userIds);
        Map<String, SysUser> userMap = users.stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));

        populateInfo(responses, userMap);
    }

    private void collectUserIds(List<CommentResponse> responses, Set<String> userIds) {
        for (CommentResponse c : responses) {
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

    private void populateInfo(List<CommentResponse> responses, Map<String, SysUser> userMap) {
        for (CommentResponse c : responses) {
            if (c.getIsAnonymous() != null && c.getIsAnonymous() == 1) {
                // Keep anonymous
            } else {
                SysUser user = userMap.get(c.getUserId());
                if (user != null) {
                    c.setNickname(user.getNickname());
                    c.setUserAvatar(user.getAvatar());
                }
            }

            if (c.getReplyUserId() != null) {
                SysUser replyUser = userMap.get(c.getReplyUserId());
                if (replyUser != null) {
                    c.setReplyUserNickname(replyUser.getNickname());
                }
            }

            if (c.getChildren() != null) {
                populateInfo(c.getChildren(), userMap);
            }
        }
    }

    private void fillLikeStatus(List<CommentResponse> responses, String currentUserId) {
        for (CommentResponse c : responses) {
            String key = "comment:like:" + c.getId() + ":" + currentUserId;
            c.setIsLiked(stringRedisTemplate.hasKey(key));
            if (c.getChildren() != null) {
                fillLikeStatus(c.getChildren(), currentUserId);
            }
        }
    }
}
