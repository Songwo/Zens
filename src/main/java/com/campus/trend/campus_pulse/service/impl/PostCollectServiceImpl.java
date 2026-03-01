package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.PostCollect;
import com.campus.trend.campus_pulse.mapper.PostCollectMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.service.PostCollectService;
import com.campus.trend.campus_pulse.service.LevelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PostCollectServiceImpl extends ServiceImpl<PostCollectMapper, PostCollect>
        implements PostCollectService {

    private final PostMapper postMapper;
    private final LevelService levelService;
    private final com.campus.trend.campus_pulse.service.NotificationService notificationService;

    public PostCollectServiceImpl(PostMapper postMapper, LevelService levelService,
            com.campus.trend.campus_pulse.service.NotificationService notificationService) {
        this.postMapper = postMapper;
        this.levelService = levelService;
        this.notificationService = notificationService;
    }

    /**
     * Song：判断用户是否已收藏该帖子
     *
     * Song：说明
     * Song：说明
     * Song：说明
     */
    @Override
    public boolean isCollect(String postId, String userId) {
        Long count = lambdaQuery()
                .eq(PostCollect::getPostId, postId)
                .eq(PostCollect::getUserId, userId)
                .count();
        return count != null && count > 0;
    }

    /**
     * Song：说明
     *
     * Song：说明
     * Song：说明
     * Song：说明
     * Song：说明
     */
    @Override
    public IPage<Post> getPostCollectWithPage(String userId, int page, int pageSize) {
        // Song：1. 查询该用户所有的收藏记录（分页）
        Page<PostCollect> postCollectPage = new Page<>(page, pageSize);
        IPage<PostCollect> postCollects = lambdaQuery()
                .eq(PostCollect::getUserId, userId)
                .orderByDesc(PostCollect::getCreateTime)
                .page(postCollectPage);

        // Song：2. 如果没有收藏记录，返回空分页
        if (postCollects.getRecords() == null || postCollects.getRecords().isEmpty()) {
            log.info("用户 [{}] 暂无收藏记录", userId);
            Page<Post> emptyPage = new Page<>(page, pageSize);
            emptyPage.setTotal(0);
            return emptyPage;
        }

        // Song：说明
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
     * Song：说明
     * Song：说明
     * Song：说明
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean collectPost(String postId, String userId) {
        // Song：1. 检查是否已收藏
        if (isCollect(postId, userId)) {
            log.warn("用户 [{}] 已经收藏过帖子 [{}]", userId, postId);
            return false;
        }

        // Song：2. 创建收藏记录
        PostCollect postCollect = new PostCollect()
                .setPostId(postId)
                .setUserId(userId)
                .setCreateTime(LocalDateTime.now());

        boolean saved = save(postCollect);

        // Song：3. 更新帖子的收藏数
        if (saved) {
            Post post = postMapper.selectById(postId);
            if (post != null) {
                int currentCollectCount = post.getCollectCount() != null ? post.getCollectCount() : 0;
                post.setCollectCount(currentCollectCount + 1);
                postMapper.updateById(post);
                log.info("用户 [{}] 收藏帖子 [{}] 成功，当前收藏数: {}", userId, postId, currentCollectCount + 1);

                // Song：被收藏经验 +3（给帖子作者）
                levelService.addExperience(post.getUserId(), 3, "被收藏");

                // Song：发送收藏通知（不给自己发通知）
                if (!post.getUserId().equals(userId)) {
                    notificationService.sendFavoriteNotification(post.getUserId(), userId, postId);
                }
            }
        }

        return saved;
    }

    /**
     * Song：用户取消收藏帖子
     *
     * Song：说明
     * Song：说明
     * Song：说明
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean uncollectPost(String postId, String userId) {
        // Song：1. 检查是否已收藏
        if (!isCollect(postId, userId)) {
            log.warn("用户 [{}] 未收藏过帖子 [{}]", userId, postId);
            return false;
        }

        // Song：2. 删除收藏记录
        boolean removed = lambdaUpdate()
                .eq(PostCollect::getPostId, postId)
                .eq(PostCollect::getUserId, userId)
                .remove();

        // Song：3. 更新帖子的收藏数
        if (removed) {
            Post post = postMapper.selectById(postId);
            if (post != null) {
                int currentCollectCount = post.getCollectCount() != null ? post.getCollectCount() : 0;
                post.setCollectCount(Math.max(0, currentCollectCount - 1));
                postMapper.updateById(post);
                log.info("用户 [{}] 取消收藏帖子 [{}] 成功，当前收藏数: {}", userId, postId, Math.max(0, currentCollectCount - 1));
            }
        }

        return removed;
    }

    /**
     * Song：切换收藏状态（收藏/取消收藏）
     *
     * Song：说明
     * Song：说明
     * Song：说明
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
}
