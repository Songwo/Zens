package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.monitor.CacheMonitor;
import com.campus.trend.campus_pulse.monitor.HikariMonitor;
import com.campus.trend.campus_pulse.monitor.JvmMonitor;
import com.campus.trend.campus_pulse.monitor.PerformanceEventRecorder;
import com.campus.trend.campus_pulse.monitor.WebVitalRecorder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 性能监控管理接口
 * 提供系统性能指标查询
 */
@Tag(name = "性能监控", description = "系统性能监控接口")
@RestController
@RequestMapping({"/admin/performance", "/api/admin/performance"})
@RequiredArgsConstructor
public class PerformanceController {

    private final HikariMonitor hikariMonitor;
    private final CacheMonitor cacheMonitor;
    private final JvmMonitor jvmMonitor;
    private final PerformanceEventRecorder performanceEventRecorder;
    private final WebVitalRecorder webVitalRecorder;

    @Operation(summary = "获取数据库连接池状态")
    @GetMapping("/hikari")
    public Result<HikariMonitor.HikariStatus> getHikariStatus() {
        HikariMonitor.HikariStatus status = hikariMonitor.getPoolStatus();
        return Result.success(status);
    }

    @Operation(summary = "获取缓存统计信息")
    @GetMapping("/cache")
    public Result<Map<String, CacheMonitor.CacheStatistics>> getCacheStats() {
        Map<String, CacheMonitor.CacheStatistics> stats = cacheMonitor.getAllCacheStats();
        return Result.success(stats);
    }

    @Operation(summary = "获取JVM状态")
    @GetMapping("/jvm")
    public Result<JvmMonitor.JvmStatus> getJvmStatus() {
        JvmMonitor.JvmStatus status = jvmMonitor.getJvmStatus();
        return Result.success(status);
    }

    @Operation(summary = "获取性能监控汇总")
    @GetMapping("/summary")
    public Result<Map<String, Object>> getPerformanceSummary() {
        Map<String, Object> summary = new HashMap<>();

        // HikariCP状态
        HikariMonitor.HikariStatus hikariStatus = hikariMonitor.getPoolStatus();
        if (hikariStatus != null) {
            summary.put("hikari", Map.of(
                "active", hikariStatus.active(),
                "idle", hikariStatus.idle(),
                "total", hikariStatus.total(),
                "maxPoolSize", hikariStatus.maxPoolSize(),
                "waiting", hikariStatus.waiting(),
                "activeRatio", String.format("%.1f%%", hikariStatus.activeRatio() * 100),
                "healthy", hikariStatus.isHealthy()
            ));
        }

        // 缓存统计
        Map<String, CacheMonitor.CacheStatistics> cacheStats = cacheMonitor.getAllCacheStats();
        Map<String, Map<String, Object>> cacheInfo = new HashMap<>();

        cacheStats.forEach((name, stats) -> {
            cacheInfo.put(name, Map.of(
                "size", stats.size(),
                "hits", stats.hits(),
                "misses", stats.misses(),
                "total", stats.total(),
                "hitRatio", String.format("%.1f%%", stats.hitRatio() * 100),
                "effective", stats.isEffective()
            ));
        });

        summary.put("cache", cacheInfo);
        summary.put("slowRequests", performanceEventRecorder.listSlowRequests(null, null, 10));
        summary.put("slowSql", performanceEventRecorder.listSlowSql(null, null, 10));
        summary.put("webVitals", webVitalRecorder.summary());

        // JVM信息
        JvmMonitor.JvmStatus jvmStatus = jvmMonitor.getJvmStatus();
        if (jvmStatus != null) {
            summary.put("jvm", Map.of(
                "heapUsedMB", jvmStatus.heapUsedMB(),
                "heapMaxMB", jvmStatus.heapMaxMB(),
                "heapUsageRatio", String.format("%.1f%%", jvmStatus.heapUsageRatio() * 100),
                "nonHeapUsedMB", jvmStatus.nonHeapUsedMB(),
                "threadCount", jvmStatus.threadCount(),
                "peakThreadCount", jvmStatus.peakThreadCount(),
                "processors", jvmStatus.processors(),
                "healthy", jvmStatus.isHealthy()
            ));
        }

        return Result.success(summary);
    }

    @Operation(summary = "查询慢接口记录")
    @GetMapping("/slow-requests")
    public Result<Object> getSlowRequests(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Long minMs,
                                          @RequestParam(required = false) Integer limit) {
        return Result.success(performanceEventRecorder.listSlowRequests(keyword, minMs, limit));
    }

    @Operation(summary = "查询慢SQL记录")
    @GetMapping("/slow-sql")
    public Result<Object> getSlowSql(@RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) Long minMs,
                                     @RequestParam(required = false) Integer limit) {
        return Result.success(performanceEventRecorder.listSlowSql(keyword, minMs, limit));
    }

    @Operation(summary = "查询 Web Vitals 明细")
    @GetMapping("/web-vitals")
    public Result<Object> getWebVitals(@RequestParam(required = false) String metric,
                                       @RequestParam(required = false) String route,
                                       @RequestParam(required = false) Integer limit) {
        return Result.success(webVitalRecorder.list(metric, route, limit));
    }
}
