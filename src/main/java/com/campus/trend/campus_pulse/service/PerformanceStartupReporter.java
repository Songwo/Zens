package com.campus.trend.campus_pulse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 性能优化启动报告
 * 应用启动完成后打印优化配置摘要
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PerformanceStartupReporter {

    @EventListener(ApplicationReadyEvent.class)
    public void printOptimizationReport() {
        log.info("Campus Pulse 性能优化已启用: cache,bloom,batch-query,rate-limit,metrics,hikari");
        log.info("监控端点: prometheus=/actuator/prometheus, performance=/api/admin/performance/summary");
        log.debug("优化详情: Caffeine(user/section/tag), Redisson BloomFilter/RateLimiter, BatchUserService, SlowSQL=300ms, slow-api=1000ms, cache warmup");
    }
}
