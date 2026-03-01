package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.service.CacheManagementService;
import com.campus.trend.campus_pulse.utils.PermissionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Song：缓存管理控制器
 * Song：提供缓存清理等管理接口
 */
@Slf4j
@RestController
@RequestMapping({"/admin/cache", "/api/admin/cache"})
public class CacheManagementController {

    private final CacheManagementService cacheManagementService;

    @Autowired
    public CacheManagementController(CacheManagementService cacheManagementService) {
        this.cacheManagementService = cacheManagementService;
    }

    /**
     * Song：清除所有标签缓存
     */
    @DeleteMapping("/tag/clear")
    public Result<?> clearTagCache() {
        Result<?> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        cacheManagementService.clearAllTagCache();
        return Result.success("标签缓存已清除");
    }

    /**
     * Song：说明
     */
    @DeleteMapping("/token/clear")
    public Result<?> clearTokenCache() {
        Result<?> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        cacheManagementService.clearAllTokenCache();
        return Result.success("Token缓存已清除");
    }

    /**
     * Song：清除指定模式的缓存
     */
    @DeleteMapping("/clear")
    public Result<?> clearCache(@RequestParam("pattern") String pattern) {
        Result<?> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        if (pattern == null || pattern.isBlank() || pattern.length() > 120) {
            return Result.error(ResultCode.PARAM_ERROR, "缓存模式不能为空且长度不能超过120");
        }
        long cleared = cacheManagementService.clearCacheByPattern(pattern);
        return Result.success("已清除 " + cleared + " 个缓存");
    }

    /**
     * Song：获取缓存概览
     */
    @GetMapping("/overview")
    public Result<Map<String, Long>> getCacheOverview() {
        Result<Map<String, Long>> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        return Result.success(cacheManagementService.getCacheOverview());
    }

    /**
     * Song：按模式统计缓存数量
     */
    @GetMapping("/count")
    public Result<Long> countByPattern(@RequestParam("pattern") String pattern) {
        Result<Long> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        if (pattern == null || pattern.isBlank() || pattern.length() > 120) {
            return Result.error(ResultCode.PARAM_ERROR, "缓存模式不能为空且长度不能超过120");
        }
        return Result.success(cacheManagementService.countCacheByPattern(pattern));
    }

    private <T> Result<T> requireAdmin() {
        if (!PermissionUtils.isAdmin()) {
            return Result.error(ResultCode.NO_PERMISSION, "仅管理员可执行缓存管理操作");
        }
        return null;
    }
}
