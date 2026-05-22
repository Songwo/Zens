package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.entity.MediaFile;
import com.campus.trend.campus_pulse.mapper.MediaFileMapper;
import com.campus.trend.campus_pulse.service.MediaFileService;
import com.campus.trend.campus_pulse.utils.IdUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MediaFileServiceImpl extends ServiceImpl<MediaFileMapper, MediaFile> implements MediaFileService {

    @Override
    public MediaFile findActiveBySha256(String sha256) {
        if (!StringUtils.hasText(sha256)) {
            return null;
        }
        return baseMapper.selectActiveBySha256(sha256.trim());
    }

    @Override
    public MediaFile findActiveByFileKey(String fileKey) {
        if (!StringUtils.hasText(fileKey)) {
            return null;
        }
        return lambdaQuery()
                .eq(MediaFile::getFileKey, fileKey.trim())
                .eq(MediaFile::getStatus, 1)
                .last("LIMIT 1")
                .one();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MediaFile saveCompletedFile(MediaFile mediaFile) {
        MediaFile existing = lambdaQuery()
                .eq(MediaFile::getFileKey, mediaFile.getFileKey())
                .last("LIMIT 1")
                .one();
        LocalDateTime now = LocalDateTime.now();
        mediaFile.setUpdateTime(now);
        mediaFile.setStatus(1);
        if (existing != null) {
            mediaFile.setId(existing.getId());
            mediaFile.setCreateTime(existing.getCreateTime() != null ? existing.getCreateTime() : now);
            updateById(mediaFile);
            return getById(existing.getId());
        }
        mediaFile.setId(StringUtils.hasText(mediaFile.getId()) ? mediaFile.getId() : IdUtils.genId("MEDIA"));
        mediaFile.setCreateTime(now);
        save(mediaFile);
        return mediaFile;
    }

    @Override
    public Page<MediaFile> pageFiles(int pageNum, int pageSize, String keyword, String mediaType, String bizType, Integer status) {
        Page<MediaFile> page = new Page<>(Math.max(1, pageNum), Math.max(1, pageSize));
        LambdaQueryWrapper<MediaFile> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(q -> q.like(MediaFile::getOriginalName, keyword)
                    .or()
                    .like(MediaFile::getFileKey, keyword));
        }
        wrapper.eq(StringUtils.hasText(mediaType), MediaFile::getMediaType, mediaType)
                .eq(StringUtils.hasText(bizType), MediaFile::getBizType, bizType);
        if (status != null) {
            wrapper.eq(MediaFile::getStatus, status);
        }
        wrapper.orderByDesc(MediaFile::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean softDelete(String id) {
        if (!StringUtils.hasText(id)) {
            return false;
        }
        MediaFile mediaFile = getById(id);
        if (mediaFile == null || mediaFile.getStatus() == null || mediaFile.getStatus() == 0) {
            return false;
        }
        mediaFile.setStatus(0);
        mediaFile.setUpdateTime(LocalDateTime.now());
        return updateById(mediaFile);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int softDeleteBatch(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int affected = 0;
        for (String id : ids) {
            if (softDelete(id)) {
                affected++;
            }
        }
        return affected;
    }

    @Override
    public Map<String, Object> buildStats() {
        long imageCount = lambdaQuery().eq(MediaFile::getStatus, 1).eq(MediaFile::getMediaType, "image").count();
        long videoCount = lambdaQuery().eq(MediaFile::getStatus, 1).eq(MediaFile::getMediaType, "video").count();
        long fileTotalSizeBytes = readSumSizeByStatus(1);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("imageCount", imageCount);
        stats.put("videoCount", videoCount);
        stats.put("fileTotalSizeBytes", fileTotalSizeBytes);
        stats.put("uploadSuccessCount", imageCount + videoCount);
        stats.put("uploadFailureCount", 0);
        stats.put("chunkInProgressCount", 0);
        return stats;
    }

    @Override
    public List<MediaFile> listRecent(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        return lambdaQuery()
                .eq(MediaFile::getStatus, 1)
                .orderByDesc(MediaFile::getCreateTime)
                .last("LIMIT " + limit)
                .list();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long rebuildActiveAccessUrls(String publicBaseUrl) {
        String normalizedBaseUrl = normalizeBaseUrl(publicBaseUrl);
        if (!StringUtils.hasText(normalizedBaseUrl)) {
            return 0L;
        }
        List<MediaFile> files = lambdaQuery()
                .eq(MediaFile::getStatus, 1)
                .list();
        LocalDateTime now = LocalDateTime.now();
        long updated = 0L;
        for (MediaFile file : files) {
            String nextUrl = normalizedBaseUrl + "/" + normalizeFileKey(file.getFileKey());
            if (nextUrl.equals(file.getAccessUrl())) {
                continue;
            }
            file.setAccessUrl(nextUrl);
            file.setUpdateTime(now);
            if (updateById(file)) {
                updated++;
            }
        }
        return updated;
    }

    private long readSumSizeByStatus(int status) {
        QueryWrapper<MediaFile> wrapper = new QueryWrapper<>();
        wrapper.select("COALESCE(SUM(size_bytes), 0) AS totalSizeBytes")
                .eq("status", status);
        List<Map<String, Object>> rows = baseMapper.selectMaps(wrapper);
        if (rows.isEmpty()) {
            return 0L;
        }
        Object value = rows.get(0).get("totalSizeBytes");
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }

    private String normalizeBaseUrl(String publicBaseUrl) {
        if (!StringUtils.hasText(publicBaseUrl)) {
            return "";
        }
        String normalized = publicBaseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeFileKey(String fileKey) {
        if (!StringUtils.hasText(fileKey)) {
            return "";
        }
        String normalized = fileKey.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}
