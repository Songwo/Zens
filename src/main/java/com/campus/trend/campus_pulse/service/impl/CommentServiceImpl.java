package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.dto.request.CreateCommentRequest;
import com.campus.trend.campus_pulse.entity.SysComment;
import com.campus.trend.campus_pulse.entity.SysPost;
import com.campus.trend.campus_pulse.mapper.SysCommentMapper;
import com.campus.trend.campus_pulse.mapper.SysPostMapper;
import com.campus.trend.campus_pulse.service.CommentService;
import com.campus.trend.campus_pulse.service.ContentSecurityService;
import com.campus.trend.campus_pulse.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<SysCommentMapper, SysComment> implements CommentService {

    private final SysPostMapper sysPostMapper;
    private final UserProfileService userProfileService;
    private final ContentSecurityService contentSecurityService;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addComment(CreateCommentRequest request, String userId) {
        SysComment comment = new SysComment();
        comment.setPostId(request.getPostId());
        // 数据库 user_id 字段非空，使用 "0" 作为匿名用户的占位ID
        comment.setUserId(userId == null ? "0" : userId);

        // 内容安全检查
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

        // 更新帖子评论数
        SysPost post = sysPostMapper.selectById(comment.getPostId());
        if (post != null) {
            post.setCommentCount(post.getCommentCount() + 1);
            sysPostMapper.updateById(post);
        }

        // 更新用户画像（只对已登录用户）
        if (userId != null) {
            userProfileService.addContribution(userId, 2); // 评论贡献值+2
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

        // 只有评论作者可以删除
        if (!comment.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除该评论");
        }

        this.removeById(commentId);

        // 更新帖子评论数
        SysPost post = sysPostMapper.selectById(comment.getPostId());
        if (post != null) {
            post.setCommentCount(Math.max(0, post.getCommentCount() - 1));
            sysPostMapper.updateById(post);
        }
    }

    @Override
    public Object getCommentsByPostId(String postId, Integer pageNo, Integer pageSize) {
        // 1. 分页查询根评论 (parentId = "0")
        Page<SysComment> page = new Page<>(pageNo, pageSize);
        Page<SysComment> rootPage = this.page(page, Wrappers.<SysComment>lambdaQuery()
                .eq(SysComment::getPostId, postId)
                .eq(SysComment::getParentId, "0")
                .orderByDesc(SysComment::getCreateTime)); // 按时间倒序，新的在前面

        List<SysComment> roots = rootPage.getRecords();

        // 2. 为每个根评论递归查询子评论
        // 注意：这里为了性能，每页只加载10条根评论的子树，循环查询是可以接受的，或者可以用 IN 查询优化
        // 简单实现：循环递归
        roots.forEach(this::fillChildren);

        return rootPage;
    }

    /**
     * 递归填充子节点
     * 为了避免一次加载过多，这里只查库取出直接子节点，然后递归
     * 如果层级很深，建议改为前端按需加载（点击“查看回复”再查）
     * 这里为了保持原有结构，先做全量递归
     */
    private void fillChildren(SysComment parent) {
        // 查询当前节点的直接子节点
        List<SysComment> children = this.list(Wrappers.<SysComment>lambdaQuery()
                .eq(SysComment::getParentId, parent.getId())
                .orderByAsc(SysComment::getCreateTime)); // 子评论通常按时间正序

        if (children != null && !children.isEmpty()) {
            parent.setChildren(children);
            // 递归填充孙子节点
            children.forEach(this::fillChildren);
        }
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
            // 取消点赞
            stringRedisTemplate.delete(key);
            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
            // 减少作者获赞数
            userProfileService.decrementLikesReceived(comment.getUserId());
        } else {
            // 点赞
            stringRedisTemplate.opsForValue().set(key, "1");
            comment.setLikeCount(comment.getLikeCount() + 1);
            // 增加作者获赞数
            userProfileService.incrementLikesReceived(comment.getUserId());
        }
        updateById(comment);
    }

    /**
     * 递归查找子节点
     */
    private List<SysComment> getChildrens(SysComment root, List<SysComment> all) {
        return all.stream()
                // 筛选出所有 parentId 等于当前 root id 的数据
                .filter(comment -> Objects.equals(comment.getParentId(), root.getId()))
                .peek(comment -> {
                    // 递归：继续去找当前子节点的子节点
                    comment.setChildren(getChildrens(comment, all));
                })
                .collect(Collectors.toList());
    }
}
