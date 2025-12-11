package com.campus.trend.campus_pulse.scheduled;

import com.campus.trend.campus_pulse.service.TrendStatService;
import com.campus.trend.campus_pulse.service.ViewLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 趋势统计定时任务调度器
 */
@Component
@EnableScheduling
@Slf4j
@RequiredArgsConstructor
public class TrendTaskScheduler {

    private final TrendStatService trendStatService;
    private final ViewLogService viewLogService;

    /**
     * 每日凌晨 01:00 执行
     * 生成昨日的趋势统计数据
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void generateDailyStatsTask() {
        log.info("开始执行 [每日趋势统计生成] 定时任务...");
        long start = System.currentTimeMillis();

        try {
            trendStatService.generateDailyStats();
        } catch (Exception e) {
            log.error("每日趋势统计生成失败", e);
        }

        log.info("执行 [每日趋势统计生成] 完成, 耗时: {} ms", System.currentTimeMillis() - start);
    }

    /**
     * 每周日凌晨 03:00 执行
     * 清理超过 90 天的旧浏览日志
     */
    @Scheduled(cron = "0 0 3 ? * SUN")
    public void cleanOldLogsTask() {
        log.info("开始执行 [清理旧日志] 定时任务...");

        try {
            // 清理 90 天前的浏览日志
            viewLogService.cleanOldLogs(90);

            // 下一步可以清理旧的趋势统计数据 (比如保留1年的日报)
            // trendStatService.deleteStatsBefore(...);

        } catch (Exception e) {
            log.error("清理旧日志失败", e);
        }

        log.info("执行 [清理旧日志] 完成");
    }

    /**
     * 每 10 分钟刷新一次实时热榜缓存 (示例)
     * 如果热榜计算比较重，可以在这里定时计算并缓存到 Redis 或 SysTrendStat 中
     */
    @Scheduled(fixedRate = 10 * 60 * 1000)
    public void refreshHotTrendsCache() {
        // log.debug("刷新实时热榜缓存...");
        // 实际逻辑可调用 service 层方法
    }

}
