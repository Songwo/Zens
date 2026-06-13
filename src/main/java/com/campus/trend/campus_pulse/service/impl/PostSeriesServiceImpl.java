package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.entity.*;
import com.campus.trend.campus_pulse.mapper.*;
import com.campus.trend.campus_pulse.service.PostSeriesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 帖子系列服务实现
 */
@Slf4j
@Service
public class PostSeriesServiceImpl extends ServiceImpl<PostSeriesMapper, PostSeries> implements PostSeriesService {

    @Autowired
    private PostSeriesItemMapper postSeriesItemMapper;

    @Autowired
    private PostMapper postMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSeries(String title, String description, String userId) {
        PostSeries series = new PostSeries()
                .setUserId(userId)
                .setTitle(title)
                .setDescription(description)
                .setStatus(1)
                .setPostCount(0)
                .setViewCount(0)
                .setLikeCount(0)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());

        this.save(series);
        log.info("创建帖子系列: seriesId={}, title={}, userId={}", series.getId(), title, userId);
        return series.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addPostToSeries(Long seriesId, String postId, String userId) {
        // 1. 验证系列存在且是创建者
        PostSeries series = this.getById(seriesId);
        if (series == null) {
            throw new BusinessException("系列不存在");
        }
        if (!series.getUserId().equals(userId)) {
            throw new BusinessException("只有系列创建者可以添加帖子");
        }

        // 2. 验证帖子存在
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("帖子不存在");
        }

        // 3. 检查是否已添加
        LambdaQueryWrapper<PostSeriesItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostSeriesItem::getSeriesId, seriesId)
                .eq(PostSeriesItem::getPostId, postId);
        if (postSeriesItemMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("帖子已在该系列中");
        }

        // 4. 获取当前最大排序号
        LambdaQueryWrapper<PostSeriesItem> maxWrapper = new LambdaQueryWrapper<>();
        maxWrapper.eq(PostSeriesItem::getSeriesId, seriesId)
                .orderByDesc(PostSeriesItem::getOrderIndex)
                .last("LIMIT 1");
        PostSeriesItem lastItem = postSeriesItemMapper.selectOne(maxWrapper);
        int nextOrder = lastItem == null ? 0 : lastItem.getOrderIndex() + 1;

        // 5. 添加关联
        PostSeriesItem item = new PostSeriesItem()
                .setSeriesId(seriesId)
                .setPostId(postId)
                .setOrderIndex(nextOrder)
                .setAddedAt(LocalDateTime.now());
        postSeriesItemMapper.insert(item);

        // 6. 更新系列统计
        series.setPostCount(series.getPostCount() + 1);
        this.updateById(series);

        log.info("添加帖子到系列: seriesId={}, postId={}, order={}", seriesId, postId, nextOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removePostFromSeries(Long seriesId, String postId, String userId) {
        // 1. 验证系列存在且是创建者
        PostSeries series = this.getById(seriesId);
        if (series == null) {
            throw new BusinessException("系列不存在");
        }
        if (!series.getUserId().equals(userId)) {
            throw new BusinessException("只有系列创建者可以移除帖子");
        }

        // 2. 删除关联
        LambdaQueryWrapper<PostSeriesItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostSeriesItem::getSeriesId, seriesId)
                .eq(PostSeriesItem::getPostId, postId);
        int deleted = postSeriesItemMapper.delete(wrapper);

        if (deleted > 0) {
            // 3. 更新系列统计
            series.setPostCount(Math.max(0, series.getPostCount() - 1));
            this.updateById(series);
            log.info("从系列移除帖子: seriesId={}, postId={}", seriesId, postId);
        }
    }

    @Override
    public List<Object> getSeriesPosts(Long seriesId) {
        LambdaQueryWrapper<PostSeriesItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostSeriesItem::getSeriesId, seriesId)
                .orderByAsc(PostSeriesItem::getOrderIndex);

        List<PostSeriesItem> items = postSeriesItemMapper.selectList(wrapper);

        return items.stream()
                .map(item -> {
                    Post post = postMapper.selectById(item.getPostId());
                    return post;
                })
                .filter(post -> post != null)
                .collect(Collectors.toList());
    }

    @Override
    public List<PostSeries> getUserSeries(String userId) {
        LambdaQueryWrapper<PostSeries> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostSeries::getUserId, userId)
                .eq(PostSeries::getStatus, 1)
                .orderByDesc(PostSeries::getCreatedAt);
        return this.list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSeriesOrder(Long seriesId, String postId, Integer newOrderIndex, String userId) {
        // 1. 验证权限
        PostSeries series = this.getById(seriesId);
        if (series == null) {
            throw new BusinessException("系列不存在");
        }
        if (!series.getUserId().equals(userId)) {
            throw new BusinessException("只有系列创建者可以调整顺序");
        }

        // 2. 更新排序
        LambdaQueryWrapper<PostSeriesItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostSeriesItem::getSeriesId, seriesId)
                .eq(PostSeriesItem::getPostId, postId);

        PostSeriesItem item = postSeriesItemMapper.selectOne(wrapper);
        if (item != null) {
            item.setOrderIndex(newOrderIndex);
            postSeriesItemMapper.updateById(item);
            log.info("更新系列排序: seriesId={}, postId={}, newOrder={}", seriesId, postId, newOrderIndex);
        }
    }
}
