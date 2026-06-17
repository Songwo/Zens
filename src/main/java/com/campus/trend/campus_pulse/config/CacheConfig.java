package com.campus.trend.campus_pulse.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

/**
 * 缓存配置 - 本地Caffeine缓存（L1）+ Redis（L2）
 *
 * Caffeine用于热点数据（用户信息、板块、标签等），减少Redis网络开销
 * Redis用于分布式缓存共享和持久化
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Caffeine本地缓存管理器（主缓存）
     * 适用场景：
     * - user:info: 用户基础信息（5分钟过期，1万用户）
     * - section:list: 板块列表（10分钟过期）
     * - tag:hot: 热门标签（5分钟过期）
     */
    @Bean
    @Primary
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            "user:info",      // 用户信息缓存
            "section:list",   // 板块列表缓存
            "tag:hot",        // 热门标签缓存
            "level:info",     // 等级信息缓存
            "user:badge"      // 用户徽章缓存
        );

        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10_000)                        // 最多缓存10000个条目
            .expireAfterWrite(5, TimeUnit.MINUTES)      // 写入后5分钟过期
            .expireAfterAccess(3, TimeUnit.MINUTES)     // 访问后3分钟未访问则过期
            .recordStats()                              // 启用统计（监控命中率）
            .removalListener((key, value, cause) -> {
                log.debug("缓存移除: key={}, cause={}", key, cause);
            }));

        return cacheManager;
    }

    /**
     * 长期缓存配置（用于不常变化的数据）
     * 适用场景：板块列表、系统配置等
     */
    @Bean("longTermCacheManager")
    public CacheManager longTermCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            "section:all",
            "config:system"
        );

        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(30, TimeUnit.MINUTES)     // 30分钟过期
            .recordStats());

        return cacheManager;
    }
}
