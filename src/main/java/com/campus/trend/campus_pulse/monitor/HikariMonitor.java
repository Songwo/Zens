package com.campus.trend.campus_pulse.monitor;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HikariCP连接池监控
 * 定期检查连接池状态，超过阈值时告警
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(HikariDataSource.class)
public class HikariMonitor {

    private final DataSource dataSource;
    private final MeterRegistry meterRegistry;
    private final AtomicBoolean metricsRegistered = new AtomicBoolean(false);

    private static final double ACTIVE_THRESHOLD = 0.8; // 活跃连接超过80%告警
    private static final double WAITING_THRESHOLD = 5;   // 等待线程超过5个告警

    /**
     * 注册Hikari指标到Prometheus
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void registerMetrics() {
        if (!metricsRegistered.compareAndSet(false, true)) {
            return;
        }
        if (!(dataSource instanceof HikariDataSource)) {
            log.warn("数据源不是HikariDataSource，跳过监控注册");
            return;
        }

        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        HikariPoolMXBean poolProxy = hikariDataSource.getHikariPoolMXBean();

        if (poolProxy == null) {
            log.warn("无法获取HikariPoolMXBean，跳过监控注册");
            return;
        }

        // 活跃连接数
        Gauge.builder("campus.hikari.connections.active", poolProxy, HikariPoolMXBean::getActiveConnections)
            .description("Campus active Hikari connections")
            .register(meterRegistry);

        // 空闲连接数
        Gauge.builder("campus.hikari.connections.idle", poolProxy, HikariPoolMXBean::getIdleConnections)
            .description("Campus idle Hikari connections")
            .register(meterRegistry);

        // 总连接数
        Gauge.builder("campus.hikari.connections.total", poolProxy, HikariPoolMXBean::getTotalConnections)
            .description("Campus total Hikari connections")
            .register(meterRegistry);

        // 等待连接的线程数
        Gauge.builder("campus.hikari.connections.waiting", poolProxy, HikariPoolMXBean::getThreadsAwaitingConnection)
            .description("Campus threads waiting for Hikari connection")
            .register(meterRegistry);

        log.info("HikariCP补充监控指标已注册到Prometheus");
    }

    /**
     * 定期检查连接池状态（每分钟）
     */
    @Scheduled(fixedRate = 60000)
    public void checkPoolHealth() {
        if (!(dataSource instanceof HikariDataSource)) {
            return;
        }

        try {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            HikariPoolMXBean poolProxy = hikariDataSource.getHikariPoolMXBean();

            if (poolProxy == null) {
                return;
            }

            int active = poolProxy.getActiveConnections();
            int idle = poolProxy.getIdleConnections();
            int total = poolProxy.getTotalConnections();
            int waiting = poolProxy.getThreadsAwaitingConnection();
            int maxPoolSize = hikariDataSource.getMaximumPoolSize();

            // 计算活跃连接占比
            double activeRatio = total > 0 ? (double) active / maxPoolSize : 0;

            // 记录当前状态
            log.debug("HikariCP状态: active={}, idle={}, total={}/{}, waiting={}",
                active, idle, total, maxPoolSize, waiting);

            // 活跃连接超过阈值告警
            if (activeRatio > ACTIVE_THRESHOLD) {
                log.warn("数据库连接池告警: 活跃连接过高 {}/{} ({}%), 等待线程: {}",
                    active, maxPoolSize, String.format("%.1f", activeRatio * 100), waiting);
            }

            // 等待线程过多告警
            if (waiting > WAITING_THRESHOLD) {
                log.warn("数据库连接池告警: 等待连接的线程过多 {}，可能需要增加连接池大小", waiting);
            }

            // 连接数接近上限告警
            if (total >= maxPoolSize - 2) {
                log.warn("数据库连接池告警: 连接数已达上限 {}/{}，建议扩容", total, maxPoolSize);
            }

        } catch (Exception e) {
            log.error("检查HikariCP健康状态失败: {}", e.getMessage());
        }
    }

    /**
     * 获取连接池详细状态（用于接口查询）
     */
    public HikariStatus getPoolStatus() {
        if (!(dataSource instanceof HikariDataSource)) {
            return null;
        }

        try {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            HikariPoolMXBean poolProxy = hikariDataSource.getHikariPoolMXBean();

            if (poolProxy == null) {
                return null;
            }

            return new HikariStatus(
                poolProxy.getActiveConnections(),
                poolProxy.getIdleConnections(),
                poolProxy.getTotalConnections(),
                hikariDataSource.getMaximumPoolSize(),
                poolProxy.getThreadsAwaitingConnection()
            );

        } catch (Exception e) {
            log.error("获取HikariCP状态失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 连接池状态数据类
     */
    public record HikariStatus(
        int active,
        int idle,
        int total,
        int maxPoolSize,
        int waiting
    ) {
        public double activeRatio() {
            return maxPoolSize > 0 ? (double) active / maxPoolSize : 0;
        }

        public boolean isHealthy() {
            return activeRatio() < ACTIVE_THRESHOLD && waiting < WAITING_THRESHOLD;
        }
    }
}
