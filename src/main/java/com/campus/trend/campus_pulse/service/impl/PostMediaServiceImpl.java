package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.dto.media.MediaObject;
import com.campus.trend.campus_pulse.entity.PostMedia;
import com.campus.trend.campus_pulse.mapper.PostMediaMapper;
import com.campus.trend.campus_pulse.service.PostMediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostMediaServiceImpl extends ServiceImpl<PostMediaMapper, PostMedia> implements PostMediaService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<PostMedia> saveForPost(String postId, List<MediaObject> mediaList) {
        if (!StringUtils.hasText(postId)) {
            return Collections.emptyList();
        }
        // 覆盖式：先清空旧的引用（对应前端编辑帖子时删掉一张图的场景）。
        deleteByPostId(postId);
        if (mediaList == null || mediaList.isEmpty()) {
            return Collections.emptyList();
        }
        List<PostMedia> rows = new ArrayList<>(mediaList.size());
        LocalDateTime now = LocalDateTime.now();
        int index = 0;
        for (MediaObject media : mediaList) {
            if (media == null || !StringUtils.hasText(media.getAccessUrl())) {
                continue;
            }
            PostMedia row = new PostMedia()
                    .setPostId(postId)
                    .setFileId(media.getFileId())
                    .setMediaType(defaultIfBlank(media.getMediaType(), "image"))
                    .setAccessUrl(media.getAccessUrl())
                    .setCoverUrl(media.getCoverUrl())
                    .setMimeType(media.getMimeType())
                    .setOriginalName(media.getOriginalName())
                    .setSizeBytes(media.getSizeBytes())
                    .setWidth(media.getWidth())
                    .setHeight(media.getHeight())
                    .setDurationSeconds(media.getDurationSeconds())
                    .setSortOrder(index++)
                    .setStatus(1);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            rows.add(row);
        }
        if (!rows.isEmpty()) {
            saveBatch(rows);
        }
        return rows;
    }

    @Override
    public List<PostMedia> listByPostId(String postId) {
        if (!StringUtils.hasText(postId)) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<PostMedia> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostMedia::getPostId, postId)
                .eq(PostMedia::getStatus, 1)
                .orderByAsc(PostMedia::getSortOrder)
                .orderByAsc(PostMedia::getId);
        return list(wrapper);
    }

    @Override
    public List<PostMedia> listByPostIds(Collection<String> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<PostMedia> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(PostMedia::getPostId, postIds)
                .eq(PostMedia::getStatus, 1)
                .orderByAsc(PostMedia::getSortOrder)
                .orderByAsc(PostMedia::getId);
        return list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByPostId(String postId) {
        if (!StringUtils.hasText(postId)) {
            return;
        }
        LambdaQueryWrapper<PostMedia> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostMedia::getPostId, postId);
        remove(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByFileId(String fileId) {
        if (!StringUtils.hasText(fileId)) {
            return 0;
        }
        LambdaQueryWrapper<PostMedia> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostMedia::getFileId, fileId);
        return (int) count(wrapper) > 0 ? (remove(wrapper) ? 1 : 0) : 0;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
