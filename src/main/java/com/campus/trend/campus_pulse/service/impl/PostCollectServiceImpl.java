package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.PostCollect;
import com.campus.trend.campus_pulse.mapper.PostCollectMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.service.PostCollectService;
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

    public PostCollectServiceImpl(PostMapper postMapper) {
        this.postMapper = postMapper;
    }

    /**
     * 判断用户是否已收藏该帖子
     *
     * @param postId 帖子ID
     * @param userId 用户ID
     * @return true-已收藏, false-未收藏
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
     * 根据用户ID分页获取用户收藏的帖子
     *
     * @param userId   用户ID
     * @param page     页码（从1开始）
     * @param pageSize 每页大小
     * @return 分页的收藏帖子列表
     */
    @Override
    public IPage<Post> getPostCollectWithPage(String userId, int page, int pageSize) {
        // 1. 查询该用户所有的收藏记录（分页）
        Page<PostCollect> postCollectPage = new Page<>(page, pageSize);
        IPage<PostCollect> postCollects = lambdaQuery()
                .eq(PostCollect::getUserId, userId)
                .orderByDesc(PostCollect::getCreateTime)
                .page(postCollectPage);

        // 2. 如果没有收藏记录，返回空分页
        if (postCollects.getRecords() == null || postCollects.getRecords().isEmpty()) {
            log.info("用户 [{}] 暂无收藏记录", userId);
            Page<Post> emptyPage = new Page<>(page, pageSize);
            emptyPage.setTotal(0);
            return emptyPage;
        }

        // 3. 提取所有帖子ID
        List<String> postIds = postCollects.getRecords().stream()
                .map(PostCollect::getPostId)
                .collect(Collectors.toList());

        // 4. 批量查询帖子信息（只查询状态正常的帖子）
        List<Post> posts = postMapper.selectBatchIds(postIds).stream()
                .filter(post -> post.getStatus() != null && post.getStatus() == 1)
                .collect(Collectors.toList());

        // 5. 封装为分页结果
        Page<Post> resultPage = new Page<>(page, pageSize);
        resultPage.setRecords(posts);
        resultPage.setTotal(postCollects.getTotal()); // 使用原始收藏总数
        resultPage.setCurrent(postCollects.getCurrent());
        resultPage.setSize(postCollects.getSize());

        log.info("用户 [{}] 第 {} 页，每页 {} 条，共 {} 条收藏记录，返回 {} 条有效帖子",
                userId, page, pageSize, postCollects.getTotal(), posts.size());

        return resultPage;
    }

    /**
     * 用户收藏帖子
     *
     * @param postId 帖子ID
     * @param userId 用户ID
     * @return true-收藏成功, false-已经收藏过
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean collectPost(String postId, String userId) {
        // 1. 检查是否已收藏
        if (isCollect(postId, userId)) {
            log.warn("用户 [{}] 已经收藏过帖子 [{}]", userId, postId);
            return false;
        }

        // 2. 创建收藏记录
        PostCollect postCollect = new PostCollect()
                .setPostId(postId)
                .setUserId(userId)
                .setCreateTime(LocalDateTime.now());

        boolean saved = save(postCollect);

        // 3. 更新帖子的收藏数
        if (saved) {
            Post post = postMapper.selectById(postId);
            if (post != null) {
                int currentCollectCount = post.getCollectCount() != null ? post.getCollectCount() : 0;
                post.setCollectCount(currentCollectCount + 1);
                postMapper.updateById(post);
                log.info("用户 [{}] 收藏帖子 [{}] 成功，当前收藏数: {}", userId, postId, currentCollectCount + 1);
            }
        }

        return saved;
    }

    /**
     * 用户取消收藏帖子
     *
     * @param postId 帖子ID
     * @param userId 用户ID
     * @return true-取消成功, false-未收藏过
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean uncollectPost(String postId, String userId) {
        // 1. 检查是否已收藏
        if (!isCollect(postId, userId)) {
            log.warn("用户 [{}] 未收藏过帖子 [{}]", userId, postId);
            return false;
        }

        // 2. 删除收藏记录
        boolean removed = lambdaUpdate()
                .eq(PostCollect::getPostId, postId)
                .eq(PostCollect::getUserId, userId)
                .remove();

        // 3. 更新帖子的收藏数
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
     * 切换收藏状态（收藏/取消收藏）
     *
     * @param postId 帖子ID
     * @param userId 用户ID
     * @return true-已收藏, false-已取消收藏
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
