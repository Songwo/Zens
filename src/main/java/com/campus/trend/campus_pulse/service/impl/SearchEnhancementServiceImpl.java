package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.entity.SearchHistory;
import com.campus.trend.campus_pulse.mapper.SearchHistoryMapper;
import com.campus.trend.campus_pulse.service.SearchEnhancementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 搜索增强服务实现
 */
@Slf4j
@Service
public class SearchEnhancementServiceImpl extends ServiceImpl<SearchHistoryMapper, SearchHistory> implements SearchEnhancementService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String HOT_SEARCH_KEY = "search:hot:keywords";
    private static final String SEARCH_SUGGEST_KEY = "search:suggest:";

    @Override
    public void recordSearch(String userId, String keyword, Integer resultCount, Map<String, Object> filters) {
        // 1. 记录到数据库
        SearchHistory history = new SearchHistory()
                .setUserId(userId)
                .setKeyword(keyword)
                .setResultCount(resultCount)
                .setSearchFilters(filters)
                .setCreatedAt(LocalDateTime.now());
        this.save(history);

        // 2. 更新热门搜索词（Redis ZSet）
        try {
            redisTemplate.opsForZSet().incrementScore(HOT_SEARCH_KEY, keyword, 1);
            // 保留前100个热门词
            Long size = redisTemplate.opsForZSet().size(HOT_SEARCH_KEY);
            if (size != null && size > 100) {
                redisTemplate.opsForZSet().removeRange(HOT_SEARCH_KEY, 0, size - 101);
            }
        } catch (Exception e) {
            log.error("更新热门搜索词失败", e);
        }

        // 3. 更新搜索建议（前缀匹配）
        try {
            String[] words = keyword.split("\\s+");
            for (String word : words) {
                if (word.length() >= 2) {
                    String suggestKey = SEARCH_SUGGEST_KEY + word.substring(0, Math.min(2, word.length()));
                    redisTemplate.opsForZSet().incrementScore(suggestKey, keyword, 1);
                    redisTemplate.expire(suggestKey, 7, TimeUnit.DAYS);
                }
            }
        } catch (Exception e) {
            log.error("更新搜索建议失败", e);
        }

        log.info("记录搜索历史: userId={}, keyword={}, resultCount={}", userId, keyword, resultCount);
    }

    @Override
    public List<SearchHistory> getUserSearchHistory(String userId, Integer limit) {
        LambdaQueryWrapper<SearchHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SearchHistory::getUserId, userId)
                .orderByDesc(SearchHistory::getCreatedAt)
                .last("LIMIT " + (limit != null ? limit : 20));
        return this.list(wrapper);
    }

    @Override
    public List<Map<String, Object>> getHotSearchKeywords(Integer limit) {
        try {
            Set<ZSetOperations.TypedTuple<Object>> tuples = redisTemplate.opsForZSet()
                    .reverseRangeWithScores(HOT_SEARCH_KEY, 0, (limit != null ? limit : 10) - 1);

            if (tuples == null || tuples.isEmpty()) {
                return new ArrayList<>();
            }

            return tuples.stream()
                    .map(tuple -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("keyword", tuple.getValue());
                        map.put("searchCount", tuple.getScore() != null ? tuple.getScore().intValue() : 0);
                        return map;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("获取热门搜索词失败", e);
            return new ArrayList<>();
        }
    }

    @Override
    public void clearUserSearchHistory(String userId) {
        LambdaQueryWrapper<SearchHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SearchHistory::getUserId, userId);
        this.remove(wrapper);
        log.info("清除搜索历史: userId={}", userId);
    }

    @Override
    public void recordSearchClick(String userId, String keyword, String postId) {
        // 更新最近一条搜索记录的点击
        LambdaQueryWrapper<SearchHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SearchHistory::getUserId, userId)
                .eq(SearchHistory::getKeyword, keyword)
                .orderByDesc(SearchHistory::getCreatedAt)
                .last("LIMIT 1");

        SearchHistory history = this.getOne(wrapper);
        if (history != null && history.getClickedPostId() == null) {
            history.setClickedPostId(postId);
            this.updateById(history);
        }
    }

    @Override
    public List<String> getSearchSuggestions(String keyword, Integer limit) {
        if (keyword == null || keyword.length() < 2) {
            return new ArrayList<>();
        }

        try {
            String prefix = keyword.substring(0, Math.min(2, keyword.length()));
            String suggestKey = SEARCH_SUGGEST_KEY + prefix;

            Set<ZSetOperations.TypedTuple<Object>> tuples = redisTemplate.opsForZSet()
                    .reverseRangeWithScores(suggestKey, 0, (limit != null ? limit : 10) - 1);

            if (tuples == null || tuples.isEmpty()) {
                return new ArrayList<>();
            }

            return tuples.stream()
                    .map(tuple -> tuple.getValue().toString())
                    .filter(k -> k.toLowerCase().contains(keyword.toLowerCase()))
                    .limit(limit != null ? limit : 10)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("获取搜索建议失败", e);
            return new ArrayList<>();
        }
    }
}
