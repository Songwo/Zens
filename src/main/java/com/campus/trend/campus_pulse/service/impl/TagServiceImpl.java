package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.entity.Tag;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.mapper.TagMapper;
import com.campus.trend.campus_pulse.service.TagService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final PostMapper postMapper;

    @Autowired
    public TagServiceImpl(StringRedisTemplate redisTemplate, PostMapper postMapper) {
        this.redisTemplate = redisTemplate;
        this.postMapper = postMapper;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        this.objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public int recalculatePostCounts() {
        return baseMapper.recalculateAllPostCounts();
    }

    /**
     * Song：获取或创建标签（不存在时自动创建）
     *
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Tag getOrCreateTag(String tagName) {
        // Song：去除空格并转小写进行查询（不区分大小写）
        String normalizedName = tagName.trim();

        // Song：查询是否已存在
        Tag existingTag = lambdaQuery()
                .eq(Tag::getName, normalizedName)
                .one();

        if (existingTag != null) {
            return existingTag;
        }

        // Song：不存在则创建新标签（用户生成标签）
        Tag newTag = new Tag()
                .setName(normalizedName)
                .setType(2) // Song：2:用户生成
                .setHeat(0)
                .setCreateTime(LocalDateTime.now());

        save(newTag);
        log.info("创建新标签: [{}], ID: {}", normalizedName, newTag.getId());

        return newTag;
    }

    /**
     * Song：增加标签热度（同时清除缓存）
     *
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "tag:hot", allEntries = true)
    public void increaseHeat(Long tagId) {
        Tag tag = getById(tagId);
        if (tag != null) {
            int newHeat = (tag.getHeat() != null ? tag.getHeat() : 0) + 1;
            tag.setHeat(newHeat);
            updateById(tag);

            // Song：清除热门标签缓存
            clearHotTagsCache();

            log.debug("标签 [{}] 热度增加，当前热度: {}", tag.getName(), newHeat);
        }
    }

    /**
     * Song：减少标签热度（同时清除缓存）
     *
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "tag:hot", allEntries = true)
    public void decreaseHeat(Long tagId) {
        Tag tag = getById(tagId);
        if (tag != null) {
            int currentHeat = tag.getHeat() != null ? tag.getHeat() : 0;
            int newHeat = Math.max(0, currentHeat - 1);
            tag.setHeat(newHeat);
            updateById(tag);

            // Song：清除热门标签缓存
            clearHotTagsCache();

            log.debug("标签 [{}] 热度减少，当前热度: {}", tag.getName(), newHeat);
        }
    }

    /**
     * Song：清除热门标签缓存
     */
    private void clearHotTagsCache() {
        try {
            Set<String> keys = scanKeys("tag:hot:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("已清除 {} 个热门标签缓存", keys.size());
            }
        } catch (Exception e) {
            log.warn("清除缓存失败: {}", e.getMessage());
        }
    }

    @Override
    @Cacheable(value = "tag:hot", key = "#limit", unless = "#result == null || #result.isEmpty()")
    public List<Tag> getHotTags(int limit) {
        String cacheKey = "tag:hot:" + limit;

        try {
            // Song：1. 尝试从缓存获取
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                List<Tag> cachedTags = objectMapper.readValue(cached, new TypeReference<List<Tag>>() {
                });
                log.debug("从缓存获取热门标签 TOP{}", limit);
                return cachedTags;
            }
        } catch (Exception e) {
            // Song：缓存解析失败（可能是旧格式），清除该缓存
            log.warn("读取缓存失败，清除缓存: {}", e.getMessage());
            try {
                redisTemplate.delete(cacheKey);
            } catch (Exception ex) {
                // Song：忽略删除失败
            }
        }

        // Song：2. 查询数据库
        List<Tag> hotTags = lambdaQuery()
                .orderByDesc(Tag::getHeat)
                .orderByDesc(Tag::getCreateTime)
                .last("LIMIT " + limit)
                .list();

        // Song：3. 写入缓存（5分钟过期）
        try {
            String jsonValue = objectMapper.writeValueAsString(hotTags);
            redisTemplate.opsForValue().set(cacheKey, jsonValue,5 ,TimeUnit.MINUTES);
            log.debug("热门标签已缓存 TOP{}", limit);
        } catch (Exception e) {
            log.warn("写入缓存失败: {}", e.getMessage());
        }

        log.info("获取热门标签 TOP{}: {}", limit,
                hotTags.stream().map(Tag::getName).collect(Collectors.toList()));

        return hotTags;
    }

    /**
     * Song：搜索标签
     *
     */
    @Override
    public List<Tag> searchTags(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<Tag> results = lambdaQuery()
                .like(Tag::getName, keyword.trim())
                .orderByDesc(Tag::getHeat)
                .last("LIMIT 20")
                .list();

        // post_count 为冗余列,查询时随行返回,由 TagHeatDecayTask 每小时校准
        log.info("搜索标签 [{}], 找到 {} 个结果", keyword, results.size());

        return results;
    }

    private Set<String> scanKeys(String pattern) {
        return redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> keys = new java.util.LinkedHashSet<>();
            ScanOptions options = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(100)
                    .build();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    byte[] raw = cursor.next();
                    if (raw != null && raw.length > 0) {
                        keys.add(new String(raw, StandardCharsets.UTF_8));
                    }
                }
            }
            return keys;
        });
    }

    /**
     * Song：批量获取或创建标签
     *
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Tag> batchGetOrCreateTags(List<String> tagNames) {
        List<Tag> tags = new ArrayList<>();

        for (String tagName : tagNames) {
            if (tagName != null && !tagName.trim().isEmpty()) {
                Tag tag = getOrCreateTag(tagName.trim());
                tags.add(tag);
            }
        }

        return tags;
    }
}
