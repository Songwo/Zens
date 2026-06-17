package com.campus.trend.campus_pulse.monitor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JVM监控组件
 * 监控内存、线程、GC等关键指标
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JvmMonitor {

    private final MeterRegistry meterRegistry;
    private final AtomicBoolean metricsRegistered = new AtomicBoolean(false);

    /**
     * 注册JVM指标到Prometheus
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void registerMetrics() {
        if (!metricsRegistered.compareAndSet(false, true)) {
            return;
        }
        Runtime runtime = Runtime.getRuntime();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        // JVM内存使用
        Gauge.builder("campus.jvm.heap.used", () -> {
            MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
            return heapUsage.getUsed();
        })
            .description("Campus JVM heap memory used (bytes)")
            .baseUnit("bytes")
            .register(meterRegistry);

        Gauge.builder("campus.jvm.heap.max", () -> {
            MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
            return heapUsage.getMax();
        })
            .description("Campus JVM heap memory max (bytes)")
            .baseUnit("bytes")
            .register(meterRegistry);

        // 线程数
        Gauge.builder("campus.jvm.threads.current", threadMXBean::getThreadCount)
            .description("Campus current thread count")
            .register(meterRegistry);

        Gauge.builder("campus.jvm.threads.peak", threadMXBean::getPeakThreadCount)
            .description("Campus peak thread count")
            .register(meterRegistry);

        Gauge.builder("campus.jvm.threads.daemon", threadMXBean::getDaemonThreadCount)
            .description("Campus daemon thread count")
            .register(meterRegistry);

        log.info("JVM补充监控指标已注册到Prometheus");
    }

    /**
     * 定期检查JVM健康状态（每2分钟）
     */
    @Scheduled(fixedRate = 120000)
    public void checkJvmHealth() {
        try {
            Runtime runtime = Runtime.getRuntime();
            MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

            // 堆内存
            MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
            long usedHeap = heapUsage.getUsed() / 1024 / 1024;  // MB
            long maxHeap = heapUsage.getMax() / 1024 / 1024;    // MB
            double heapUsageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();

            // 非堆内存（方法区、元空间等）
            MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
            long usedNonHeap = nonHeapUsage.getUsed() / 1024 / 1024;

            // 线程数
            int threadCount = threadMXBean.getThreadCount();
            int peakThreadCount = threadMXBean.getPeakThreadCount();

            log.debug("JVM状态: Heap={}MB/{}MB ({}%), NonHeap={}MB, Threads={}/{}",
                usedHeap, maxHeap, String.format("%.1f", heapUsageRatio * 100), usedNonHeap, threadCount, peakThreadCount);

            // 堆内存告警（超过85%）
            if (heapUsageRatio > 0.85) {
                log.warn("JVM堆内存告警: 使用率 {}% ({}MB/{}MB), 建议扩大堆内存或检查内存泄漏",
                    String.format("%.1f", heapUsageRatio * 100), usedHeap, maxHeap);
            }

            // 线程数告警（超过500）
            if (threadCount > 500) {
                log.warn("JVM线程数告警: 当前{}个线程，峰值{}个，可能存在线程泄漏",
                    threadCount, peakThreadCount);
            }

            // 检查是否发生Full GC（通过堆内存使用率判断）
            if (heapUsageRatio > 0.90) {
                log.error("JVM内存严重不足: 使用率 {}%, 可能即将发生Full GC或OOM",
                    String.format("%.1f", heapUsageRatio * 100));
            }

        } catch (Exception e) {
            log.error("检查JVM健康状态失败: {}", e.getMessage());
        }
    }

    /**
     * 获取JVM状态（用于接口查询）
     */
    public JvmStatus getJvmStatus() {
        try {
            Runtime runtime = Runtime.getRuntime();
            MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

            MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
            MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();

            return new JvmStatus(
                heapUsage.getUsed() / 1024 / 1024,
                heapUsage.getMax() / 1024 / 1024,
                nonHeapUsage.getUsed() / 1024 / 1024,
                threadMXBean.getThreadCount(),
                threadMXBean.getPeakThreadCount(),
                runtime.availableProcessors()
            );

        } catch (Exception e) {
            log.error("获取JVM状态失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * JVM状态数据类
     */
    public record JvmStatus(
        long heapUsedMB,
        long heapMaxMB,
        long nonHeapUsedMB,
        int threadCount,
        int peakThreadCount,
        int processors
    ) {
        public double heapUsageRatio() {
            return heapMaxMB > 0 ? (double) heapUsedMB / heapMaxMB : 0;
        }

        public boolean isHealthy() {
            return heapUsageRatio() < 0.85 && threadCount < 500;
        }
    }
}
