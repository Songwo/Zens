package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.entity.SysTag;
import com.campus.trend.campus_pulse.mapper.SysTagMapper;
import com.campus.trend.campus_pulse.service.TagService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TagServiceImpl extends ServiceImpl<SysTagMapper, SysTag> implements TagService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public TagServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        // 配置ObjectMapper支持Java 8日期时间类型
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        this.objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * 获取或创建标签（不存在时自动创建）
     *
     * @param tagName 标签名称
     * @return 标签对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysTag getOrCreateTag(String tagName) {
        // 去除空格并转小写进行查询（不区分大小写）
        String normalizedName = tagName.trim();

        // 查询是否已存在
        SysTag existingTag = lambdaQuery()
                .eq(SysTag::getName, normalizedName)
                .one();

        if (existingTag != null) {
            return existingTag;
        }

        // 不存在则创建新标签（用户生成标签）
        SysTag newTag = new SysTag()
                .setName(normalizedName)
                .setType(2) // 2:用户生成
                .setHeat(0)
                .setCreateTime(LocalDateTime.now());

        save(newTag);
        log.info("创建新标签: [{}], ID: {}", normalizedName, newTag.getId());

        return newTag;
    }

    /**
     * 增加标签热度（同时清除缓存）
     *
     * @param tagId 标签ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void increaseHeat(Long tagId) {
        SysTag tag = getById(tagId);
        if (tag != null) {
            int newHeat = (tag.getHeat() != null ? tag.getHeat() : 0) + 1;
            tag.setHeat(newHeat);
            updateById(tag);

            // 清除热门标签缓存
            clearHotTagsCache();

            log.debug("标签 [{}] 热度增加，当前热度: {}", tag.getName(), newHeat);
        }
    }

    /**
     * 减少标签热度（同时清除缓存）
     *
     * @param tagId 标签ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void decreaseHeat(Long tagId) {
        SysTag tag = getById(tagId);
        if (tag != null) {
            int currentHeat = tag.getHeat() != null ? tag.getHeat() : 0;
            int newHeat = Math.max(0, currentHeat - 1);
            tag.setHeat(newHeat);
            updateById(tag);

            // 清除热门标签缓存
            clearHotTagsCache();

            log.debug("标签 [{}] 热度减少，当前热度: {}", tag.getName(), newHeat);
        }
    }

    /**
     * 清除热门标签缓存
     */
    private void clearHotTagsCache() {
        try {
            Set<String> keys = redisTemplate.keys("tag:hot:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("已清除 {} 个热门标签缓存", keys.size());
            }
        } catch (Exception e) {
            log.warn("清除缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 获取热门标签（带Redis缓存）
     *
     * @param limit 返回数量
     * @return 热门标签列表
     */
    @Override
    public List<SysTag> getHotTags(int limit) {
        String cacheKey = "tag:hot:" + limit;

        try {
            // 1. 尝试从缓存获取
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                List<SysTag> cachedTags = objectMapper.readValue(cached, new TypeReference<List<SysTag>>() {
                });
                log.debug("从缓存获取热门标签 TOP{}", limit);
                return cachedTags;
            }
        } catch (Exception e) {
            // 缓存解析失败（可能是旧格式），清除该缓存
            log.warn("读取缓存失败，清除缓存: {}", e.getMessage());
            try {
                redisTemplate.delete(cacheKey);
            } catch (Exception ex) {
                // 忽略删除失败
            }
        }

        // 2. 查询数据库
        List<SysTag> hotTags = lambdaQuery()
                .orderByDesc(SysTag::getHeat)
                .orderByDesc(SysTag::getCreateTime)
                .last("LIMIT " + limit)
                .list();

        // 3. 写入缓存（5分钟过期）
        try {
            String jsonValue = objectMapper.writeValueAsString(hotTags);
            redisTemplate.opsForValue().set(cacheKey, jsonValue,5 ,TimeUnit.MINUTES);
            log.debug("热门标签已缓存 TOP{}", limit);
        } catch (Exception e) {
            log.warn("写入缓存失败: {}", e.getMessage());
        }

        log.info("获取热门标签 TOP{}: {}", limit,
                hotTags.stream().map(SysTag::getName).collect(Collectors.toList()));

        return hotTags;
    }

    /**
     * 搜索标签
     *
     * @param keyword 关键词
     * @return 匹配的标签列表
     */
    @Override
    public List<SysTag> searchTags(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<SysTag> results = lambdaQuery()
                .like(SysTag::getName, keyword.trim())
                .orderByDesc(SysTag::getHeat)
                .last("LIMIT 20")
                .list();

        log.info("搜索标签 [{}], 找到 {} 个结果", keyword, results.size());

        return results;
    }

    /**
     * 批量获取或创建标签
     *
     * @param tagNames 标签名称列表
     * @return 标签列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SysTag> batchGetOrCreateTags(List<String> tagNames) {
        List<SysTag> tags = new ArrayList<>();

        for (String tagName : tagNames) {
            if (tagName != null && !tagName.trim().isEmpty()) {
                SysTag tag = getOrCreateTag(tagName.trim());
                tags.add(tag);
            }
        }

        return tags;
    }
}
