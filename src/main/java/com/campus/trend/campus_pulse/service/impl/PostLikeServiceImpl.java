package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.PostLike;
import com.campus.trend.campus_pulse.mapper.PostLikeMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.service.PostLikeService;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.service.LevelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PostLikeServiceImpl extends ServiceImpl<PostLikeMapper, PostLike> implements PostLikeService {

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
        Long count = lambdaQuery()
                .eq(PostLike::getPostId, postId)
                .eq(PostLike::getUserId, userId)
                .count();
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
                .filter(post -> post.getStatus() != null && post.getStatus() == 1)
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
        // Song：1. 检查是否已点赞
        if (isLike(postId, userId)) {
            log.warn("用户 [{}] 已经点赞过帖子 [{}]", userId, postId);
            return false;
        }

        // Song：2. 创建点赞记录
        PostLike postLike = new PostLike()
                .setPostId(postId)
                .setUserId(userId)
                .setCreateTime(LocalDateTime.now());

        boolean saved = save(postLike);

        // Song：3. 更新帖子的点赞数
        if (saved) {
            Post post = postMapper.selectById(postId);
            if (post != null) {
                int currentLikeCount = post.getLikeCount() != null ? post.getLikeCount() : 0;
                post.setLikeCount(currentLikeCount + 1);
                postMapper.updateById(post);
                log.info("用户 [{}] 点赞帖子 [{}] 成功，当前点赞数: {}", userId, postId, currentLikeCount + 1);

                // Song：增加帖子作者获赞数
                userService.incrementLikesReceived(post.getUserId());

                // Song：被点赞经验 +2（给帖子作者）
                levelService.addExperience(post.getUserId(), 2, "被点赞");

                // Song：发送点赞通知（不给自己发通知）
                if (!post.getUserId().equals(userId)) {
                    notificationService.sendLikeNotification(post.getUserId(), userId, postId);
                }
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
        boolean removed = lambdaUpdate()
                .eq(PostLike::getPostId, postId)
                .eq(PostLike::getUserId, userId)
                .remove();

        // Song：3. 更新帖子的点赞数
        if (removed) {
            Post post = postMapper.selectById(postId);
            if (post != null) {
                int currentLikeCount = post.getLikeCount() != null ? post.getLikeCount() : 0;
                post.setLikeCount(Math.max(0, currentLikeCount - 1));
                postMapper.updateById(post);
                log.info("用户 [{}] 取消点赞帖子 [{}] 成功，当前点赞数: {}", userId, postId, Math.max(0, currentLikeCount - 1));

                // Song：减少帖子作者获赞数
                userService.decrementLikesReceived(post.getUserId());
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
        if (isLike(postId, userId)) {
            unlikePost(postId, userId);
            return false;
        } else {
            likePost(postId, userId);
            return true;
        }
    }
}
