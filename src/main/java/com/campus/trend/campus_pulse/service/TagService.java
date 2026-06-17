package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.entity.Tag;

import java.util.List;

public interface TagService extends IService<Tag> {

    /** 全量校准标签的 post_count 冗余列,返回受影响行数 */
    int recalculatePostCounts();

    /**
     * 获取或创建标签（不存在时自动创建）
     *
     * @param tagName 标签名称
     * @return 标签对象
     */
    Tag getOrCreateTag(String tagName);

    /**
     * 增加标签热度
     *
     * @param tagId 标签ID
     */
    void increaseHeat(Long tagId);

    /**
     * 减少标签热度
     *
     * @param tagId 标签ID
     */
    void decreaseHeat(Long tagId);

    /**
     * 获取热门标签
     *
     * @param limit 返回数量
     * @return 热门标签列表
     */
    List<Tag> getHotTags(int limit);

    /**
     * 别名方法，用于缓存预热
     */
    default List<Tag> listHotTags(int limit) {
        return getHotTags(limit);
    }

    /**
     * 搜索标签
     *
     * @param keyword 关键词
     * @return 匹配的标签列表
     */
    List<Tag> searchTags(String keyword);

    /**
     * 批量获取或创建标签
     *
     * @param tagNames 标签名称列表
     * @return 标签列表
     */
    List<Tag> batchGetOrCreateTags(List<String> tagNames);
}
