package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.entity.SearchHistory;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.SearchEnhancementService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 搜索增强控制器
 */
@RestController
@RequestMapping("/search")
@Slf4j
public class SearchEnhancementController {

    @Autowired
    private SearchEnhancementService searchEnhancementService;

    /**
     * 获取用户搜索历史
     */
    @GetMapping("/history")
    public Result<List<SearchHistory>> getHistory(@RequestParam(required = false, defaultValue = "20") Integer limit) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        List<SearchHistory> history = searchEnhancementService.getUserSearchHistory(authUser.getUser().getId(), limit);
        return Result.success(history);
    }

    /**
     * 清除搜索历史
     */
    @DeleteMapping("/history")
    public Result<?> clearHistory() {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        searchEnhancementService.clearUserSearchHistory(authUser.getUser().getId());
        return Result.success("已清除搜索历史");
    }

    /**
     * 获取热门搜索词
     */
    @GetMapping("/hot-keywords")
    public Result<List<Map<String, Object>>> getHotKeywords(@RequestParam(required = false, defaultValue = "10") Integer limit) {
        List<Map<String, Object>> hotKeywords = searchEnhancementService.getHotSearchKeywords(limit);
        return Result.success(hotKeywords);
    }

    /**
     * 获取搜索建议
     */
    @GetMapping("/suggestions")
    public Result<List<String>> getSuggestions(@RequestParam String keyword,
                                               @RequestParam(required = false, defaultValue = "10") Integer limit) {
        List<String> suggestions = searchEnhancementService.getSearchSuggestions(keyword, limit);
        return Result.success(suggestions);
    }

    /**
     * 记录搜索点击
     */
    @PostMapping("/click")
    public Result<?> recordClick(@RequestParam String keyword, @RequestParam String postId) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        searchEnhancementService.recordSearchClick(authUser.getUser().getId(), keyword, postId);
        return Result.success();
    }
}
