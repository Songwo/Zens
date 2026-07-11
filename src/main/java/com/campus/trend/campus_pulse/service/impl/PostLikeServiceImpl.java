package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.PostLike;
import com.campus.trend.campus_pulse.mapper.PostLikeMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.service.PostLikeService;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.service.LevelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PostLikeServiceImpl extends ServiceImpl<PostLikeMapper, PostLike> implements PostLikeService {

    private static final int POST_STATUS_PUBLISHED = 1;
    private static final String AUDIT_STATUS_APPROVED = "APPROVED";

    private final PostMapper postMapper;
    private final UserService userService;
    private final LevelService levelService;
    private final com.campus.trend.campus_pulse.service.NotificationService notificationService;

    public PostLikeServiceImpl(PostMapper postMapper, UserService userService, LevelService levelService,
            com.campus.trend.campus_pulse.service.NotificationService notificationService) {
        this.postMapper = postMapper;
        this.userService = userService;
        this.levelService = levelService;
        this.notificationService = notificationService;
    }

    /**
     * Song：判断用户是否已点赞该帖子
     *
     */
    @Override
    public boolean isLike(String postId, String userId) {
        Long count = baseMapper.selectCount(Wrappers.<PostLike>lambdaQuery()
                .eq(PostLike::getPostId, postId)
                .eq(PostLike::getUserId, userId));
        return count != null && count > 0;
    }

    @Override
    public IPage<Post> getPostLikeWithPage(String userId, int page, int pageSize) {
        // Song：1. 查询该用户所有的点赞记录（分页）
        Page<PostLike> postLikePage = new Page<>(page, pageSize);
        IPage<PostLike> postLikes = lambdaQuery()
                .eq(PostLike::getUserId, userId)
                .orderByDesc(PostLike::getCreateTime)
                .page(postLikePage);

        // Song：2. 如果没有点赞记录，返回空分页
        if (postLikes.getRecords() == null || postLikes.getRecords().isEmpty()) {
            log.info("用户 [{}] 暂无点赞记录", userId);
            Page<Post> emptyPage = new Page<>(page, pageSize);
            emptyPage.setTotal(0);
            return emptyPage;
        }

        List<String> postIds = postLikes.getRecords().stream()
                .map(PostLike::getPostId)
                .collect(Collectors.toList());

        // Song：4. 批量查询帖子信息（只查询状态正常的帖子）
        List<Post> posts = postMapper.selectBatchIds(postIds).stream()
                .filter(this::isPubliclyVisiblePost)
                .collect(Collectors.toList());

        // Song：5. 封装为分页结果
        Page<Post> resultPage = new Page<>(page, pageSize);
        resultPage.setRecords(posts);
        resultPage.setTotal(postLikes.getTotal()); // Song：使用原始点赞总数
        resultPage.setCurrent(postLikes.getCurrent());
        resultPage.setSize(postLikes.getSize());

        log.info("用户 [{}] 第 {} 页，每页 {} 条，共 {} 条点赞记录，返回 {} 条有效帖子",
                userId, page, pageSize, postLikes.getTotal(), posts.size());

        return resultPage;
    }

    /**
     * Song：用户点赞帖子
     *
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean likePost(String postId, String userId) {
        // Song：0. 校验帖子存在且公开可见（PENDING/REJECTED/DELETED/草稿帖不可点赞，防止绕过详情页直调接口刷经验）
        Post post = getLikablePost(postId);

        // Song：1. 检查是否已点赞
        if (isLike(postId, userId)) {
            log.warn("用户 [{}] 已经点赞过帖子 [{}]", userId, postId);
            return false;
        }

        // Song：2. 创建点赞记录（唯一约束 uk_post_like_user_post 兜底并发重复提交）
        PostLike postLike = new PostLike()
                .setPostId(postId)
                .setUserId(userId)
                .setCreateTime(LocalDateTime.now());

        boolean saved;
        try {
            saved = baseMapper.insert(postLike) > 0;
        } catch (DuplicateKeyException e) {
            log.debug("点赞记录已存在: userId={}, postId={}", userId, postId);
            return false;
        }

        // Song：3. 原子更新帖子点赞数（不整行回写，避免并发丢失更新/覆盖其它计数字段）
        if (saved) {
            postMapper.incrementLikeCount(postId);
            log.info("用户 [{}] 点赞帖子 [{}] 成功", userId, postId);

            // Song：增加帖子作者获赞数
            userService.incrementLikesReceived(post.getUserId());
            userService.incrementLikesGiven(userId);

            // Song：被点赞经验 +2（给帖子作者）
            levelService.addExperience(post.getUserId(), 2, "被点赞");

            // Song：发送点赞通知（不给自己发通知）
            if (!post.getUserId().equals(userId)) {
                notificationService.sendLikeNotification(post.getUserId(), userId, postId);
            }
        }

        return saved;
    }

    /**
     * Song：用户取消点赞帖子
     *
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unlikePost(String postId, String userId) {
        // Song：1. 检查是否已点赞
        if (!isLike(postId, userId)) {
            log.warn("用户 [{}] 未点赞过帖子 [{}]", userId, postId);
            return false;
        }

        // Song：2. 删除点赞记录
        boolean removed = baseMapper.delete(Wrappers.<PostLike>lambdaQuery()
                .eq(PostLike::getPostId, postId)
                .eq(PostLike::getUserId, userId)) > 0;

        // Song：3. 原子更新帖子的点赞数
        if (removed) {
            postMapper.decrementLikeCount(postId);
            Post post = postMapper.selectById(postId);
            if (post != null) {
                log.info("用户 [{}] 取消点赞帖子 [{}] 成功", userId, postId);

                // Song：减少帖子作者获赞数
                userService.decrementLikesReceived(post.getUserId());
                userService.decrementLikesGiven(userId);

                // Song：对称扣回被点赞经验，堵住"点赞↔取消"循环刷经验
                levelService.addExperience(post.getUserId(), -2, "取消点赞");
            }
        }

        return removed;
    }

    /**
     * Song：切换点赞状态（点赞/取消点赞）
     *
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleLike(String postId, String userId) {
        // Song：先校验帖子可点赞（存在 + 公开可见），取消点赞也要求帖子存在
        getLikablePost(postId);
        if (isLike(postId, userId)) {
            unlikePost(postId, userId);
            return false;
        } else {
            likePost(postId, userId);
            return true;
        }
    }

    private Post getLikablePost(String postId) {
        if (!StringUtils.hasText(postId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "帖子ID不能为空");
        }
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }
        if (!isPubliclyVisiblePost(post)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "该帖子当前不可点赞");
        }
        return post;
    }

    private boolean isPubliclyVisiblePost(Post post) {
        if (post == null || !Integer.valueOf(POST_STATUS_PUBLISHED).equals(post.getStatus())) {
            return false;
        }
        String auditStatus = post.getAuditStatus();
        return !StringUtils.hasText(auditStatus) || AUDIT_STATUS_APPROVED.equalsIgnoreCase(auditStatus);
    }
}
