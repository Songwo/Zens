package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.service.CacheManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 缓存管理控制器
 * 提供缓存清理等管理接口
 */
@Slf4j
@RestController
@RequestMapping("/admin/cache")
public class CacheManagementController {

    private final CacheManagementService cacheManagementService;

    @Autowired
    public CacheManagementController(CacheManagementService cacheManagementService) {
        this.cacheManagementService = cacheManagementService;
    }

    /**
     * 清除所有标签缓存
     */
    @DeleteMapping("/tag/clear")
    public Result<?> clearTagCache() {
        cacheManagementService.clearAllTagCache();
        return Result.success("标签缓存已清除");
    }

    /**
     * 清除所有Token缓存
     */
    @DeleteMapping("/token/clear")
    public Result<?> clearTokenCache() {
        cacheManagementService.clearAllTokenCache();
        return Result.success("Token缓存已清除");
    }

    /**
     * 清除指定模式的缓存
     */
    @DeleteMapping("/clear")
    public Result<?> clearCache(@RequestParam("pattern") String pattern) {
        long cleared = cacheManagementService.clearCacheByPattern(pattern);
        return Result.success("已清除 " + cleared + " 个缓存");
    }
}
