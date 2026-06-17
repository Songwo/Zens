package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.PostCollect;
import com.campus.trend.campus_pulse.mapper.PostCollectMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.service.PostCollectService;
import com.campus.trend.campus_pulse.service.LevelService;
import com.campus.trend.campus_pulse.service.post.PostCacheManager;
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
public class PostCollectServiceImpl extends ServiceImpl<PostCollectMapper, PostCollect>
        implements PostCollectService {

    private final PostMapper postMapper;
    private final PostCollectMapper postCollectMapper;
    private final LevelService levelService;
    private final com.campus.trend.campus_pulse.service.NotificationService notificationService;
    private final PostCacheManager postCacheManager;

    public PostCollectServiceImpl(PostMapper postMapper, PostCollectMapper postCollectMapper, LevelService levelService,
            com.campus.trend.campus_pulse.service.NotificationService notificationService,
            PostCacheManager postCacheManager) {
        this.postMapper = postMapper;
        this.postCollectMapper = postCollectMapper;
        this.levelService = levelService;
        this.notificationService = notificationService;
        this.postCacheManager = postCacheManager;
    }

    /**
     * Song：判断用户是否已收藏该帖子
     *
     */
    @Override
    public boolean isCollect(String postId, String userId) {
        Long count = postCollectMapper.selectCount(Wrappers.<PostCollect>lambdaQuery()
                .eq(PostCollect::getPostId, postId)
                .eq(PostCollect::getUserId, userId));
        return count != null && count > 0;
    }

    @Override
    public IPage<Post> getPostCollectWithPage(String userId, int page, int pageSize) {
        // Song：1. 查询该用户所有的收藏记录（分页）
        Page<PostCollect> postCollectPage = new Page<>(page, pageSize);
        IPage<PostCollect> postCollects = page(postCollectPage, Wrappers.<PostCollect>lambdaQuery()
                .eq(PostCollect::getUserId, userId)
                .orderByDesc(PostCollect::getCreateTime));

        // Song：2. 如果没有收藏记录，返回空分页
        if (postCollects.getRecords() == null || postCollects.getRecords().isEmpty()) {
            log.info("用户 [{}] 暂无收藏记录", userId);
            Page<Post> emptyPage = new Page<>(page, pageSize);
            emptyPage.setTotal(0);
            return emptyPage;
        }

        List<String> postIds = postCollects.getRecords().stream()
                .map(PostCollect::getPostId)
                .collect(Collectors.toList());

        // Song：4. 批量查询帖子信息（只查询状态正常的帖子）
        List<Post> posts = postMapper.selectBatchIds(postIds).stream()
                .filter(post -> post.getStatus() != null && post.getStatus() == 1)
                .collect(Collectors.toList());

        // Song：5. 封装为分页结果
        Page<Post> resultPage = new Page<>(page, pageSize);
        resultPage.setRecords(posts);
        resultPage.setTotal(postCollects.getTotal()); // Song：使用原始收藏总数
        resultPage.setCurrent(postCollects.getCurrent());
        resultPage.setSize(postCollects.getSize());

        log.info("用户 [{}] 第 {} 页，每页 {} 条，共 {} 条收藏记录，返回 {} 条有效帖子",
                userId, page, pageSize, postCollects.getTotal(), posts.size());

        return resultPage;
    }

    /**
     * Song：用户收藏帖子
     *
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean collectPost(String postId, String userId) {
        Post post = getCollectablePost(postId);

        if (isCollect(postId, userId)) {
            log.debug("用户 [{}] 已经收藏过帖子 [{}]", userId, postId);
            return false;
        }

        PostCollect postCollect = new PostCollect()
                .setPostId(postId)
                .setUserId(userId)
                .setCreateTime(LocalDateTime.now());

        boolean saved;
        try {
            saved = postCollectMapper.insert(postCollect) > 0;
        } catch (DuplicateKeyException e) {
            log.debug("收藏记录已存在: userId={}, postId={}", userId, postId);
            return false;
        }

        if (saved) {
            postMapper.incrementCollectCount(postId);
            invalidatePostCache(post);
            log.info("用户 [{}] 收藏帖子 [{}] 成功", userId, postId);

            levelService.addExperience(post.getUserId(), 3, "被收藏");

            if (!post.getUserId().equals(userId)) {
                notificationService.sendFavoriteNotification(post.getUserId(), userId, postId);
            }
        }

        return saved;
    }

    /**
     * Song：用户取消收藏帖子
     *
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean uncollectPost(String postId, String userId) {
        Post post = getExistingPost(postId);

        if (!isCollect(postId, userId)) {
            log.debug("用户 [{}] 未收藏过帖子 [{}]", userId, postId);
            return false;
        }

        boolean removed = postCollectMapper.delete(Wrappers.<PostCollect>lambdaQuery()
                .eq(PostCollect::getPostId, postId)
                .eq(PostCollect::getUserId, userId)) > 0;

        if (removed) {
            postMapper.decrementCollectCount(postId);
            invalidatePostCache(post);
            log.info("用户 [{}] 取消收藏帖子 [{}] 成功", userId, postId);
        }

        return removed;
    }

    /**
     * Song：切换收藏状态（收藏/取消收藏）
     *
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleCollect(String postId, String userId) {
        if (isCollect(postId, userId)) {
            uncollectPost(postId, userId);
            return false;
        } else {
            collectPost(postId, userId);
            return true;
        }
    }

    private Post getExistingPost(String postId) {
        if (!StringUtils.hasText(postId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "帖子ID不能为空");
        }
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }
        return post;
    }

    private Post getCollectablePost(String postId) {
        Post post = getExistingPost(postId);
        if (!isCollectable(post)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "该帖子当前不可收藏");
        }
        return post;
    }

    private boolean isCollectable(Post post) {
        if (post == null || !Integer.valueOf(1).equals(post.getStatus())) {
            return false;
        }
        String auditStatus = post.getAuditStatus();
        return !StringUtils.hasText(auditStatus)
                || "PENDING".equalsIgnoreCase(auditStatus)
                || "APPROVED".equalsIgnoreCase(auditStatus);
    }

    private void invalidatePostCache(Post post) {
        if (post == null) {
            return;
        }
        postCacheManager.invalidatePostCaches(post.getSectionId(), post.getId());
    }
}
