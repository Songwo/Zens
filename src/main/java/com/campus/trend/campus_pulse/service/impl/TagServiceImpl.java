package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.entity.SysTag;
import com.campus.trend.campus_pulse.mapper.SysTagMapper;
import com.campus.trend.campus_pulse.service.TagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TagServiceImpl extends ServiceImpl<SysTagMapper, SysTag> implements TagService {

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
     * 增加标签热度
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
            log.debug("标签 [{}] 热度增加，当前热度: {}", tag.getName(), newHeat);
        }
    }

    /**
     * 减少标签热度
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
            log.debug("标签 [{}] 热度减少，当前热度: {}", tag.getName(), newHeat);
        }
    }

    /**
     * 获取热门标签
     *
     * @param limit 返回数量
     * @return 热门标签列表
     */
    @Override
    public List<SysTag> getHotTags(int limit) {
        List<SysTag> hotTags = lambdaQuery()
                .orderByDesc(SysTag::getHeat)
                .orderByDesc(SysTag::getCreateTime)
                .last("LIMIT " + limit)
                .list();

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
