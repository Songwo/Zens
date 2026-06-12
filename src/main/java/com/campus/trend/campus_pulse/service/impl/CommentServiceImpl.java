package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.dto.request.CommentCreateReq;
import com.campus.trend.campus_pulse.dto.response.CommentResp;
import com.campus.trend.campus_pulse.entity.AnswerAdoption;
import com.campus.trend.campus_pulse.entity.Comment;
import com.campus.trend.campus_pulse.entity.CommentCollect;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.AnswerAdoptionMapper;
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
    private static final long COMMENT_RESTORE_GRACE_DAYS = 3L;
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\p{IsHan}A-Za-z0-9_.-]{2,32})");

    private final PostMapper sysPostMapper;
    private final ContentSecurityService contentSecurityService;
    private final com.campus.trend.campus_pulse.service.post.PostCacheManager postCacheManager;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    private final UserService userService;
    private final NotificationService notificationService;
    private final com.campus.trend.campus_pulse.service.PostEventService postEventService;
    private final com.campus.trend.campus_pulse.service.LevelService levelService;
    private final com.campus.trend.campus_pulse.service.AsyncTaskService asyncTaskService;
    private final SectionModeratorService sectionModeratorService;
    private final CommentCollectService commentCollectService;
    private final AnswerAdoptionMapper answerAdoptionMapper;
    private final PostSubscriptionService postSubscriptionService;

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
        comment.setCollectCount(0);
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

        // 主题追踪：通知订阅者（排除评论者/作者/本次已收到回复或@通知的人）；评论者自动订阅该帖。
        // 两者都是增强逻辑，内部吞异常，不阻断评论主流程。
        Set<String> subscriptionExcludes = new HashSet<>(alreadyMentionedUserIds);
        subscriptionExcludes.addAll(mentionedUserIds);
        postSubscriptionService.notifySubscribersOnComment(
                post.getId(), post.getTitle(), post.getUserId(), actorUserId, subscriptionExcludes);
        if (userId != null) {
            try {
                postSubscriptionService.subscribe(actorUserId, post.getId(), "auto");
            } catch (Exception e) {
                // 自动订阅失败不影响评论
            }
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreComment(String commentId, String userId) {
        Comment comment = getById(commentId);
        if (comment == null) {
            throw new BusinessException(ResultCode.FAILED, "评论不存在");
        }
        if (!AUDIT_STATUS_DELETED.equalsIgnoreCase(comment.getAuditStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该评论不在回收站中");
        }

        Post post = sysPostMapper.selectById(comment.getPostId());
        if (!canManageComment(comment, post, userId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "无权恢复该评论");
        }
        if (comment.getUpdateTime() == null
                || comment.getUpdateTime().plusDays(COMMENT_RESTORE_GRACE_DAYS).isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "已超过3天冷静期，无法恢复");
        }
        if (!isCommentablePost(post)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "该帖子当前不可恢复评论");
        }

        LocalDateTime now = LocalDateTime.now();
        lambdaUpdate()
                .set(Comment::getAuditStatus, AUDIT_STATUS_APPROVED)
                .set(Comment::getUpdateTime, now)
                .eq(Comment::getId, commentId)
                .update();

        if (post != null) {
            post.setCommentCount(post.getCommentCount() == null ? 1 : post.getCommentCount() + 1);
            post.setLastActivityAt(now);
            sysPostMapper.updateById(post);
            invalidatePostFeedCache(post.getSectionId(), post.getId());
            asyncTaskService.pushPostRepliedAsync(post.getId(), post.getSectionId(), post.getCommentCount());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editComment(String commentId, String content, String userId) {
        if (!StringUtils.hasText(content) || content.trim().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "评论内容不能为空");
        }
        if (content.length() > 2000) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "评论内容过长");
        }
        Comment comment = getById(commentId);
        if (comment == null) {
            throw new BusinessException(ResultCode.FAILED, "评论不存在");
        }
        if (AUDIT_STATUS_DELETED.equalsIgnoreCase(comment.getAuditStatus())) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "该评论已删除，无法编辑");
        }

        Post post = sysPostMapper.selectById(comment.getPostId());
        if (!canManageComment(comment, post, userId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "无权编辑该评论");
        }

        // Song：与发表评论一致地做敏感词过滤 + HTML 转义，防止存入可执行脚本
        String processed = content;
        if (contentSecurityService.containsSensitiveWords(processed)) {
            processed = contentSecurityService.filterSensitiveWords(processed);
        }
        processed = HtmlUtils.htmlEscape(processed);

        // Song：只更新内容与 editTime，绝不动 updateTime（其为软删 3 天倒计时基准）
        lambdaUpdate()
                .set(Comment::getContent, processed)
                .set(Comment::getEditTime, LocalDateTime.now())
                .eq(Comment::getId, commentId)
                .update();

        // Song：刷新缓存版本，使帖子详情/列表中的评论内容及时更新
        if (post != null) {
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
        if (com.campus.trend.campus_pulse.utils.PermissionUtils.isUserAdmin(userId)) {
            return true;
        }
        if (post != null && post.getSectionId() != null
                && sectionModeratorService.isSectionModerator(userId, post.getSectionId())) {
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

        boolean canModerateDeletedComments = StringUtils.hasText(currentUserId)
                && (currentUserId.equals(post.getUserId())
                || com.campus.trend.campus_pulse.utils.PermissionUtils.isUserAdminOrModerator(currentUserId)
                || (post.getSectionId() != null
                && sectionModeratorService.canModerateSection(currentUserId, post.getSectionId())));

        // 可恢复人员需要看到删除占位；普通用户仍只看未删根评论。
        Page<Comment> page = new Page<>(pageNo, pageSize);
        var rootQuery = Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getPostId, postId);
        rootQuery.and(wrapper -> wrapper.eq(Comment::getParentId, "0")
                .or()
                .isNull(Comment::getParentId)
                .or()
                .eq(Comment::getParentId, ""));
        if (canModerateDeletedComments) {
            // 楼主/版主/管理员可看到该帖所有已删根评论占位。
        } else if (StringUtils.hasText(currentUserId)) {
            rootQuery.and(wrapper -> wrapper.ne(Comment::getAuditStatus, AUDIT_STATUS_DELETED)
                    .or(child -> child.eq(Comment::getAuditStatus, AUDIT_STATUS_DELETED)
                            .eq(Comment::getUserId, currentUserId)));
        } else {
            rootQuery.ne(Comment::getAuditStatus, AUDIT_STATUS_DELETED);
        }
        rootQuery.orderByDesc(Comment::getCreateTime);
        Page<Comment> rootPage = this.page(page, rootQuery);

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
            boolean canSeeDeletedPlaceholder = parentIdsThatHaveChildren.contains(item.getId())
                    || canModerateDeletedComments
                    || (StringUtils.hasText(currentUserId) && currentUserId.equals(item.getUserId()));
            if (!isDeleted || canSeeDeletedPlaceholder) {
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
            Comment visibleRoot = visibleById.get(root.getId());
            Comment rootForResponse = visibleRoot != null ? visibleRoot : root;
            if (AUDIT_STATUS_DELETED.equalsIgnoreCase(rootForResponse.getAuditStatus())) {
                rootForResponse.setContent(null);
            }
            attachChildrenTree(rootForResponse, childrenByParentId, new HashSet<>());
            return mapToResponse(rootForResponse);
        }).collect(Collectors.toList());

        applyAnswerAdoptionStatus(responseList, postId);
        fillUserInfo(responseList);

        Page<CommentResp> resultPage = new Page<>(pageNo, pageSize);
        resultPage.setTotal(rootPage.getTotal());
        resultPage.setPages(rootPage.getPages());
        resultPage.setCurrent(rootPage.getCurrent());
        resultPage.setRecords(responseList);

        try {
            AuthUser authUser = SecurityUtils.getAuthenticatedUser();
            if (authUser != null) {
                String statusUserId = authUser.getUser().getId();
                fillLikeStatus(responseList, statusUserId);
                fillCollectStatus(responseList, statusUserId);
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
        if (AUDIT_STATUS_DELETED.equalsIgnoreCase(comment.getAuditStatus())) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "该评论已删除，无法点赞");
        }
        Post post = sysPostMapper.selectById(comment.getPostId());
        if (!isCommentablePost(post)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "该帖子当前不可点赞评论");
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleCollect(String commentId, String userId) {
        Comment comment = getById(commentId);
        if (comment == null) {
            throw new BusinessException(ResultCode.FAILED, "评论不存在");
        }
        if (AUDIT_STATUS_DELETED.equalsIgnoreCase(comment.getAuditStatus())) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "该评论已删除，无法收藏");
        }
        Post post = sysPostMapper.selectById(comment.getPostId());
        if (!isCommentablePost(post)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "该帖子当前不可收藏评论");
        }
        return commentCollectService.toggleCollect(commentId, userId);
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

    private void applyAnswerAdoptionStatus(List<CommentResp> responses, String postId) {
        if (responses == null || responses.isEmpty() || !StringUtils.hasText(postId)) {
            return;
        }
        try {
            AnswerAdoption adoption = answerAdoptionMapper.selectOne(
                    Wrappers.<AnswerAdoption>lambdaQuery()
                            .eq(AnswerAdoption::getPostId, postId)
                            .last("LIMIT 1")
            );
            String adoptedCommentId = adoption != null ? adoption.getCommentId() : null;
            markAdoptedComment(responses, adoptedCommentId);
        } catch (Exception ignored) {
            // 反查失败时保留 sys_comment.is_adopted 的旧字段结果。
        }
    }

    private void markAdoptedComment(List<CommentResp> responses, String adoptedCommentId) {
        for (CommentResp response : responses) {
            response.setIsAdopted(StringUtils.hasText(adoptedCommentId)
                    && adoptedCommentId.equals(response.getId()) ? 1 : 0);
            if (response.getChildren() != null) {
                markAdoptedComment(response.getChildren(), adoptedCommentId);
            }
        }
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
                    c.setUserBadgeText(user.getBadgeText());
                    c.setUserBadgeColor(user.getBadgeColor());
                    c.setUserBadgeStyle(user.getBadgeStyle());
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

    private void fillCollectStatus(List<CommentResp> responses, String currentUserId) {
        Set<String> commentIds = new HashSet<>();
        collectCommentIds(responses, commentIds);
        if (commentIds.isEmpty()) {
            return;
        }

        Set<String> collectedIds = commentCollectService.lambdaQuery()
                .eq(CommentCollect::getUserId, currentUserId)
                .in(CommentCollect::getCommentId, commentIds)
                .list()
                .stream()
                .map(CommentCollect::getCommentId)
                .collect(Collectors.toSet());
        applyCollectStatus(responses, collectedIds);
    }

    private void collectCommentIds(List<CommentResp> responses, Set<String> commentIds) {
        for (CommentResp c : responses) {
            if (StringUtils.hasText(c.getId())) {
                commentIds.add(c.getId());
            }
            if (c.getChildren() != null) {
                collectCommentIds(c.getChildren(), commentIds);
            }
        }
    }

    private void applyCollectStatus(List<CommentResp> responses, Set<String> collectedIds) {
        for (CommentResp c : responses) {
            c.setIsCollected(collectedIds.contains(c.getId()));
            if (c.getChildren() != null) {
                applyCollectStatus(c.getChildren(), collectedIds);
            }
        }
    }

    private void invalidatePostFeedCache(Long sectionId, String postId) {
        postCacheManager.invalidatePostCaches(sectionId, postId);
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
