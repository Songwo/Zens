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
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.service.*;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    private static final int POST_STATUS_PUBLISHED = 1;
    private static final String AUDIT_STATUS_PENDING = "PENDING";
    private static final String AUDIT_STATUS_APPROVED = "APPROVED";
    private static final String AUDIT_STATUS_DELETED = "DELETED";
    private static final String POST_FEED_CACHE_GLOBAL_VERSION_KEY = "post:feed:version:global";
    private static final String POST_FEED_CACHE_SECTION_VERSION_KEY_PREFIX = "post:feed:version:section:";
    private static final String POST_DETAIL_CACHE_VERSION_KEY_PREFIX = "post:detail:version:";
    private static final String POST_HEAT_RANK_VERSION_KEY = "post:heat:version";
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\p{IsHan}A-Za-z0-9_.-]{2,32})");

    private final PostMapper sysPostMapper;
    private final ContentSecurityService contentSecurityService;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    private final UserService userService;
    private final NotificationService notificationService;
    private final com.campus.trend.campus_pulse.service.PostEventService postEventService;
    private final com.campus.trend.campus_pulse.service.LevelService levelService;
    private final com.campus.trend.campus_pulse.service.AsyncTaskService asyncTaskService;
    private final SectionModeratorService sectionModeratorService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addComment(CommentCreateReq request, String userId) {
        String actorUserId = userId == null ? "0" : userId;
        Post post = sysPostMapper.selectById(request.getPostId());
        if (post == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }
        if (!isCommentablePost(post)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "该帖子当前不可评论");
        }

        Comment comment = new Comment();
        comment.setPostId(request.getPostId());
        comment.setUserId(actorUserId);

        String content = request.getContent();
        if (contentSecurityService.containsSensitiveWords(content)) {
            content = contentSecurityService.filterSensitiveWords(content);
        }
        String mentionSource = content;
        // Song：存储前进行HTML转义，避免评论内容被当作脚本执行
        content = HtmlUtils.htmlEscape(content);
        comment.setContent(content);

        comment.setParentId(request.getParentId());
        comment.setReplyUserId(request.getReplyUserId());
        comment.setIsAnonymous(request.getIsAnonymous());
        comment.setCreateTime(LocalDateTime.now());
        comment.setLikeCount(0);
        this.save(comment);

        Set<String> alreadyMentionedUserIds = new HashSet<>();
        post.setCommentCount(post.getCommentCount() + 1);
        // Song：更新最后回复时间和最后活跃时间
        LocalDateTime now = LocalDateTime.now();
        post.setLastReplyAt(now);
        post.setLastActivityAt(now);
        sysPostMapper.updateById(post);
        invalidatePostFeedCache(post.getSectionId(), post.getId());

        // 异步：推送新回复事件
        asyncTaskService.pushPostRepliedAsync(post.getId(), post.getSectionId(), post.getCommentCount());

        // 异步：评论通知 + 经验奖励
        if (!post.getUserId().equals(actorUserId)) {
            final String postAuthorId = post.getUserId();
            final String postId = post.getId();
            final String commentId = comment.getId();
            final String mentionContent = mentionSource;
            notificationService.sendCommentNotification(postAuthorId, actorUserId, postId, commentId, mentionContent);
            alreadyMentionedUserIds.add(postAuthorId);
            asyncTaskService.addExperienceAsync(postAuthorId, 3, "被评论");
        }

        if (request.getParentId() != null && !"0".equals(request.getParentId())) {
            Comment parentComment = this.getById(request.getParentId());
            if (parentComment != null && !parentComment.getUserId().equals(actorUserId)) {
                notificationService.sendMentionNotification(
                    parentComment.getUserId(),
                    actorUserId,
                    post != null ? post.getId() : null,
                    comment.getId(),
                    mentionSource
                );
                alreadyMentionedUserIds.add(parentComment.getUserId());
            }
        }

        Set<String> mentionedUserIds = resolveMentionUserIds(mentionSource, actorUserId);
        mentionedUserIds.removeAll(alreadyMentionedUserIds);
        for (String mentionedUserId : mentionedUserIds) {
            notificationService.sendMentionNotification(
                    mentionedUserId,
                    actorUserId,
                    post != null ? post.getId() : null,
                    comment.getId(),
                    mentionSource
            );
        }

        if (userId != null) {
            userService.addContribution(actorUserId, 2);
            asyncTaskService.updateLastActiveAsync(actorUserId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(String commentId, String userId) {
        Comment comment = getById(commentId);
        if (comment == null) {
            throw new BusinessException(ResultCode.FAILED, "评论不存在");
        }

        // 幂等：已软删直接返回，避免重复减 comment_count
        if ("DELETED".equalsIgnoreCase(comment.getAuditStatus())) {
            return;
        }

        Post post = sysPostMapper.selectById(comment.getPostId());
        if (!canManageComment(comment, post, userId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "无权删除该评论");
        }

        // 软删：仅更新状态与时间戳
        LocalDateTime now = LocalDateTime.now();
        lambdaUpdate()
                .set(Comment::getAuditStatus, "DELETED")
                .set(Comment::getUpdateTime, now)
                .eq(Comment::getId, commentId)
                .update();

        // 评论数 -1 + 刷新 Redis 版本
        if (post != null) {
            post.setCommentCount(Math.max(0, post.getCommentCount() - 1));
            sysPostMapper.updateById(post);
            invalidatePostFeedCache(post.getSectionId(), post.getId());
        }
    }

    private boolean canManageComment(Comment comment, Post post, String userId) {
        if (comment == null || userId == null || userId.isBlank()) {
            return false;
        }
        if (userId.equals(comment.getUserId())) {
            return true;
        }
        if (post != null && userId.equals(post.getUserId())) {
            return true;
        }
        if (com.campus.trend.campus_pulse.utils.PermissionUtils.isUserAdminOrModerator(userId)) {
            return true;
        }
        if (post != null && post.getSectionId() != null
                && sectionModeratorService.canModerateSection(userId, post.getSectionId())) {
            return true;
        }
        return false;
    }

    @Override
    public com.baomidou.mybatisplus.core.metadata.IPage<CommentResp> getCommentsByPostId(String postId,
            Integer pageNo, Integer pageSize) {
        Post post = sysPostMapper.selectById(postId);
        String currentUserId = SecurityUtils.getCurrentUserId();
        if (post == null || !canViewHiddenPostComments(currentUserId, post)) {
            return new Page<CommentResp>(pageNo, pageSize).setRecords(Collections.emptyList());
        }

        // 分页只查"未删根评论"
        Page<Comment> page = new Page<>(pageNo, pageSize);
        Page<Comment> rootPage = this.page(page, Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getPostId, postId)
                .eq(Comment::getParentId, "0")
                .ne(Comment::getAuditStatus, "DELETED")
                .orderByDesc(Comment::getCreateTime));

        List<Comment> roots = rootPage.getRecords();
        if (roots.isEmpty()) {
            return new Page<CommentResp>(pageNo, pageSize).setRecords(Collections.emptyList());
        }

        // Song：一次性拉取该帖所有评论(含已删)，用于构建子树和判断占位
        List<Comment> allComments = this.list(Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getPostId, postId)
                .orderByAsc(Comment::getCreateTime));

        // Song：被作为父引用过的 id 集合 —— 这些已删评论需要保留为占位
        Set<String> parentIdsThatHaveChildren = new HashSet<>();
        for (Comment item : allComments) {
            String pid = item.getParentId();
            if (pid != null && !pid.isBlank() && !"0".equals(pid)) {
                parentIdsThatHaveChildren.add(pid);
            }
        }

        // Song：过滤可见集合 + 已删评论的 content 抹掉
        Map<String, Comment> visibleById = new HashMap<>();
        for (Comment item : allComments) {
            boolean isDeleted = "DELETED".equalsIgnoreCase(item.getAuditStatus());
            if (!isDeleted || parentIdsThatHaveChildren.contains(item.getId())) {
                if (isDeleted) {
                    item.setContent(null);
                }
                visibleById.put(item.getId(), item);
            }
        }

        // Song：仅在 visible 集合内构建 children
        Map<String, List<Comment>> childrenByParentId = new HashMap<>();
        for (Comment item : visibleById.values()) {
            String parentId = item.getParentId();
            if (parentId == null || parentId.isBlank() || "0".equals(parentId)) {
                continue;
            }
            if (!visibleById.containsKey(parentId)) {
                continue;
            }
            childrenByParentId.computeIfAbsent(parentId, key -> new ArrayList<>()).add(item);
        }

        List<CommentResp> responseList = roots.stream().map(root -> {
            attachChildrenTree(root, childrenByParentId, new HashSet<>());
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
                String likeStatusUserId = authUser.getUser().getId();
                fillLikeStatus(responseList, likeStatusUserId);
            }
        } catch (Exception ignored) {
        }

        return resultPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleLike(String commentId, String userId) {
        Comment comment = getById(commentId);
        if (comment == null) {
            throw new BusinessException(ResultCode.FAILED, "评论不存在");
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

    private boolean isCommentablePost(Post post) {
        return isPubliclyVisiblePost(post);
    }

    private boolean canViewHiddenPostComments(String currentUserId, Post post) {
        if (isPubliclyVisiblePost(post)) {
            return true;
        }
        if (!StringUtils.hasText(currentUserId)) {
            return false;
        }
        if (currentUserId.equals(post.getUserId())) {
            return true;
        }
        if (com.campus.trend.campus_pulse.utils.PermissionUtils.isUserAdminOrModerator(currentUserId)) {
            return true;
        }
        return post.getSectionId() != null
                && sectionModeratorService.canModerateSection(currentUserId, post.getSectionId());
    }

    private boolean isPubliclyVisiblePost(Post post) {
        return post != null
                && !AUDIT_STATUS_DELETED.equalsIgnoreCase(post.getAuditStatus())
                && Integer.valueOf(POST_STATUS_PUBLISHED).equals(post.getStatus())
                && (!StringUtils.hasText(post.getAuditStatus())
                || AUDIT_STATUS_PENDING.equalsIgnoreCase(post.getAuditStatus())
                || AUDIT_STATUS_APPROVED.equalsIgnoreCase(post.getAuditStatus()));
    }

    private void attachChildrenTree(Comment parent,
                                    Map<String, List<Comment>> childrenByParentId,
                                    Set<String> pathVisited) {
        if (parent == null || parent.getId() == null) {
            return;
        }
        if (!pathVisited.add(parent.getId())) {
            return;
        }

        List<Comment> children = childrenByParentId.getOrDefault(parent.getId(), Collections.emptyList());
        if (!children.isEmpty()) {
            parent.setChildren(children);
            for (Comment child : children) {
                attachChildrenTree(child, childrenByParentId, pathVisited);
            }
        }
        pathVisited.remove(parent.getId());
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

    private void invalidatePostFeedCache(Long sectionId, String postId) {
        try {
            stringRedisTemplate.opsForValue().increment(POST_FEED_CACHE_GLOBAL_VERSION_KEY);
            stringRedisTemplate.opsForValue().increment(POST_HEAT_RANK_VERSION_KEY);
            if (sectionId != null) {
                stringRedisTemplate.opsForValue().increment(POST_FEED_CACHE_SECTION_VERSION_KEY_PREFIX + sectionId);
            }
            if (postId != null && !postId.isBlank()) {
                stringRedisTemplate.opsForValue().increment(POST_DETAIL_CACHE_VERSION_KEY_PREFIX + postId);
            }
        } catch (Exception ignored) {
            // Song：不影响评论主流程
        }
    }

    private Set<String> resolveMentionUserIds(String content, String currentUserId) {
        if (!StringUtils.hasText(content)) {
            return Collections.emptySet();
        }
        Matcher matcher = MENTION_PATTERN.matcher(content);
        Set<String> mentionTokens = new HashSet<>();
        while (matcher.find()) {
            String token = matcher.group(1);
            if (StringUtils.hasText(token)) {
                mentionTokens.add(token.trim());
            }
        }
        if (mentionTokens.isEmpty()) {
            return Collections.emptySet();
        }

        List<User> matchedUsers = userService.lambdaQuery()
                .and(wrapper -> wrapper.in(User::getUsername, mentionTokens)
                        .or()
                        .in(User::getNickname, mentionTokens))
                .list();

        Set<String> mentionUserIds = new HashSet<>();
        for (User user : matchedUsers) {
            if (user == null || !StringUtils.hasText(user.getId())) {
                continue;
            }
            if (StringUtils.hasText(currentUserId) && currentUserId.equals(user.getId())) {
                continue;
            }
            mentionUserIds.add(user.getId());
        }
        return mentionUserIds;
    }
}
