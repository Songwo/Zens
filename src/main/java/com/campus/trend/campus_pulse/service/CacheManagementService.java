package com.campus.trend.campus_pulse.service;

import java.util.Map;

/**
 * Redis缓存管理服务
 * 提供缓存清理等管理功能
 */
public interface CacheManagementService {

    /**
     * 清除所有标签相关缓存
     */
    void clearAllTagCache();

    /**
     * 清楚所有Token相关的缓存
    */
    void clearAllTokenCache();

    /**
     * 清除指定前缀的缓存
     * 
     * @param keyPattern 缓存键模式（如 "tag:*"）
     * @return 清除的数量
     */
    long clearCacheByPattern(String keyPattern);

    /**
     * Song：获取缓存概览统计
     */
    Map<String, Long> getCacheOverview();

    /**
     * Song：按模式统计缓存数量
     */
    long countCacheByPattern(String keyPattern);
}
