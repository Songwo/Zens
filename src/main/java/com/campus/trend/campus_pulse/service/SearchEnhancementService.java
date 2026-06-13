package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.entity.SearchHistory;

import java.util.List;
import java.util.Map;

/**
 * 搜索增强服务接口
 */
public interface SearchEnhancementService extends IService<SearchHistory> {

    /**
     * 记录搜索历史
     * @param userId 用户ID
     * @param keyword 关键词
     * @param resultCount 结果数量
     * @param filters 搜索过滤条件
     */
    void recordSearch(String userId, String keyword, Integer resultCount, Map<String, Object> filters);

    /**
     * 获取用户搜索历史
     * @param userId 用户ID
     * @param limit 数量限制
     * @return 搜索历史列表
     */
    List<SearchHistory> getUserSearchHistory(String userId, Integer limit);

    /**
     * 获取热门搜索词
     * @param limit 数量限制
     * @return 热门关键词列表
     */
    List<Map<String, Object>> getHotSearchKeywords(Integer limit);

    /**
     * 清除用户搜索历史
     * @param userId 用户ID
     */
    void clearUserSearchHistory(String userId);

    /**
     * 记录搜索点击
     * @param userId 用户ID
     * @param keyword 关键词
     * @param postId 点击的帖子ID
     */
    void recordSearchClick(String userId, String keyword, String postId);

    /**
     * 获取搜索建议
     * @param keyword 输入的关键词
     * @param limit 建议数量
     * @return 建议列表
     */
    List<String> getSearchSuggestions(String keyword, Integer limit);
}
