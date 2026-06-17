package com.campus.trend.campus_pulse.monitor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 缓存命中率监控
 * 监控Caffeine本地缓存的命中率和性能指标
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheMonitor {

    private final CacheManager cacheManager;
    private final MeterRegistry meterRegistry;
    private final AtomicBoolean metricsRegistered = new AtomicBoolean(false);

    /**
     * 注册缓存指标到Prometheus
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void registerMetrics() {
        if (!metricsRegistered.compareAndSet(false, true)) {
            return;
        }
        if (cacheManager == null) {
            log.warn("CacheManager未初始化，跳过缓存监控注册");
            return;
        }

        cacheManager.getCacheNames().forEach(cacheName -> {
            org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);

            if (cache instanceof CaffeineCache caffeineCache) {
                Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();

                // 缓存大小
                Gauge.builder("campus.cache.entries", nativeCache, Cache::estimatedSize)
                    .tag("cache", cacheName)
                    .description("Campus Caffeine cache estimated entries")
                    .register(meterRegistry);

                // 缓存命中率（需要启用统计）
                Gauge.builder("campus.cache.hit.ratio", nativeCache, c -> {
                    CacheStats stats = c.stats();
                    long hits = stats.hitCount();
                    long misses = stats.missCount();
                    long total = hits + misses;
                    return total > 0 ? (double) hits / total : 0;
                })
                    .tag("cache", cacheName)
                    .description("Campus Caffeine cache hit ratio")
                    .register(meterRegistry);

                log.debug("缓存监控指标已注册: {}", cacheName);
            }
        });
        log.info("缓存监控指标已注册到Prometheus");
    }

    /**
     * 定期打印缓存统计信息（每5分钟）
     */
    @Scheduled(fixedRate = 300000)
    public void printCacheStats() {
        if (cacheManager == null) {
            return;
        }

        Map<String, CacheStatistics> statsMap = new HashMap<>();

        cacheManager.getCacheNames().forEach(cacheName -> {
            org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);

            if (cache instanceof CaffeineCache caffeineCache) {
                Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
                CacheStats stats = nativeCache.stats();

                long hits = stats.hitCount();
                long misses = stats.missCount();
                long total = hits + misses;
                double hitRatio = total > 0 ? (double) hits / total : 0;
                long size = nativeCache.estimatedSize();

                CacheStatistics cacheStats = new CacheStatistics(
                    cacheName, size, hits, misses, hitRatio
                );

                statsMap.put(cacheName, cacheStats);

                // 命中率低于50%时告警
                if (total > 100 && hitRatio < 0.5) {
                    log.warn("缓存命中率过低: {} - 命中率: {}%, 命中: {}, 未命中: {}",
                        cacheName, String.format("%.1f", hitRatio * 100), hits, misses);
                }
            }
        });

        // 打印汇总信息
        if (!statsMap.isEmpty()) {
            log.debug("缓存统计汇总:");
            statsMap.forEach((name, stats) -> {
                log.debug("  {} - 大小: {}, 命中率: {}%, 命中: {}, 未命中: {}",
                    name, stats.size, String.format("%.1f", stats.hitRatio * 100), stats.hits, stats.misses);
            });
        }
    }

    /**
     * 获取所有缓存的统计信息（用于接口查询）
     */
    public Map<String, CacheStatistics> getAllCacheStats() {
        Map<String, CacheStatistics> statsMap = new HashMap<>();

        if (cacheManager == null) {
            return statsMap;
        }

        cacheManager.getCacheNames().forEach(cacheName -> {
            org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);

            if (cache instanceof CaffeineCache caffeineCache) {
                Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
                CacheStats stats = nativeCache.stats();

                long hits = stats.hitCount();
                long misses = stats.missCount();
                long total = hits + misses;
                double hitRatio = total > 0 ? (double) hits / total : 0;
                long size = nativeCache.estimatedSize();

                statsMap.put(cacheName, new CacheStatistics(
                    cacheName, size, hits, misses, hitRatio
                ));
            }
        });

        return statsMap;
    }

    /**
     * 缓存统计数据类
     */
    public record CacheStatistics(
        String name,
        long size,
        long hits,
        long misses,
        double hitRatio
    ) {
        public long total() {
            return hits + misses;
        }

        public boolean isEffective() {
            return total() > 100 && hitRatio > 0.5;
        }
    }
}
