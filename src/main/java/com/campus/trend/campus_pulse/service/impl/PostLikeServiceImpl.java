package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.entity.SysPost;
import com.campus.trend.campus_pulse.entity.SysPostLike;
import com.campus.trend.campus_pulse.mapper.SysPostLikeMapper;
import com.campus.trend.campus_pulse.mapper.SysPostMapper;
import com.campus.trend.campus_pulse.service.PostLikeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PostLikeServiceImpl extends ServiceImpl<SysPostLikeMapper, SysPostLike> implements PostLikeService {

    private final SysPostMapper postMapper;

    public PostLikeServiceImpl(SysPostMapper postMapper) {
        this.postMapper = postMapper;
    }

    /**
     * 判断用户是否已点赞该帖子
     *
     * @param postId 帖子ID
     * @param userId 用户ID
     * @return true-已点赞, false-未点赞
     */
    @Override
    public boolean isLike(String postId, String userId) {
        Long count = lambdaQuery()
                .eq(SysPostLike::getPostId, postId)
                .eq(SysPostLike::getUserId, userId)
                .count();
        return count != null && count > 0;
    }

    /**
     * 根据用户ID分页获取用户喜欢的帖子
     *
     * @param userId   用户ID
     * @param page     页码（从1开始）
     * @param pageSize 每页大小
     * @return 分页的点赞帖子列表
     */
    @Override
    public IPage<SysPost> getPostLikeWithPage(String userId, int page, int pageSize) {
        // 1. 查询该用户所有的点赞记录（分页）
        Page<SysPostLike> postLikePage = new Page<>(page, pageSize);
        IPage<SysPostLike> postLikes = lambdaQuery()
                .eq(SysPostLike::getUserId, userId)
                .orderByDesc(SysPostLike::getCreateTime)
                .page(postLikePage);

        // 2. 如果没有点赞记录，返回空分页
        if (postLikes.getRecords() == null || postLikes.getRecords().isEmpty()) {
            log.info("用户 [{}] 暂无点赞记录", userId);
            Page<SysPost> emptyPage = new Page<>(page, pageSize);
            emptyPage.setTotal(0);
            return emptyPage;
        }

        // 3. 提取所有帖子ID
        List<String> postIds = postLikes.getRecords().stream()
                .map(SysPostLike::getPostId)
                .collect(Collectors.toList());

        // 4. 批量查询帖子信息（只查询状态正常的帖子）
        List<SysPost> posts = postMapper.selectBatchIds(postIds).stream()
                .filter(post -> post.getStatus() != null && post.getStatus() == 1)
                .collect(Collectors.toList());

        // 5. 封装为分页结果
        Page<SysPost> resultPage = new Page<>(page, pageSize);
        resultPage.setRecords(posts);
        resultPage.setTotal(postLikes.getTotal()); // 使用原始点赞总数
        resultPage.setCurrent(postLikes.getCurrent());
        resultPage.setSize(postLikes.getSize());

        log.info("用户 [{}] 第 {} 页，每页 {} 条，共 {} 条点赞记录，返回 {} 条有效帖子",
                userId, page, pageSize, postLikes.getTotal(), posts.size());

        return resultPage;
    }

    /**
     * 用户点赞帖子
     *
     * @param postId 帖子ID
     * @param userId 用户ID
     * @return true-点赞成功, false-已经点赞过
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean likePost(String postId, String userId) {
        // 1. 检查是否已点赞
        if (isLike(postId, userId)) {
            log.warn("用户 [{}] 已经点赞过帖子 [{}]", userId, postId);
            return false;
        }

        // 2. 创建点赞记录
        SysPostLike postLike = new SysPostLike()
                .setPostId(postId)
                .setUserId(userId)
                .setCreateTime(LocalDateTime.now());

        boolean saved = save(postLike);

        // 3. 更新帖子的点赞数
        if (saved) {
            SysPost post = postMapper.selectById(postId);
            if (post != null) {
                int currentLikeCount = post.getLikeCount() != null ? post.getLikeCount() : 0;
                post.setLikeCount(currentLikeCount + 1);
                postMapper.updateById(post);
                log.info("用户 [{}] 点赞帖子 [{}] 成功，当前点赞数: {}", userId, postId, currentLikeCount + 1);
            }
        }

        return saved;
    }

    /**
     * 用户取消点赞帖子
     *
     * @param postId 帖子ID
     * @param userId 用户ID
     * @return true-取消成功, false-未点赞过
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unlikePost(String postId, String userId) {
        // 1. 检查是否已点赞
        if (!isLike(postId, userId)) {
            log.warn("用户 [{}] 未点赞过帖子 [{}]", userId, postId);
            return false;
        }

        // 2. 删除点赞记录
        boolean removed = lambdaUpdate()
                .eq(SysPostLike::getPostId, postId)
                .eq(SysPostLike::getUserId, userId)
                .remove();

        // 3. 更新帖子的点赞数
        if (removed) {
            SysPost post = postMapper.selectById(postId);
            if (post != null) {
                int currentLikeCount = post.getLikeCount() != null ? post.getLikeCount() : 0;
                post.setLikeCount(Math.max(0, currentLikeCount - 1));
                postMapper.updateById(post);
                log.info("用户 [{}] 取消点赞帖子 [{}] 成功，当前点赞数: {}", userId, postId, Math.max(0, currentLikeCount - 1));
            }
        }

        return removed;
    }

    /**
     * 切换点赞状态（点赞/取消点赞）
     *
     * @param postId 帖子ID
     * @param userId 用户ID
     * @return true-已点赞, false-已取消点赞
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
