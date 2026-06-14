package com.campus.trend.campus_pulse.scheduled;

import com.campus.trend.campus_pulse.config.properties.TrustLevelProperties;
import com.campus.trend.campus_pulse.service.TrustLevelService;
import com.campus.trend.campus_pulse.service.TrendScheduledTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class TrendTaskScheduler {

    private final TrendScheduledTaskService trendScheduledTaskService;
    private final TrustLevelService trustLevelService;
    private final TrustLevelProperties trustLevelProperties;

    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyLevelUpgradeTask() {
        trendScheduledTaskService.runDailyLevelUpgrade();
    }

    /**
     * 每周日凌晨 03:00 执行
     * 清理超过 90 天的旧浏览日志
     */
    @Scheduled(cron = "0 0 3 ? * SUN")
    public void cleanOldLogsTask() {
        trendScheduledTaskService.runViewLogCleanup();
    }

    /**
     * Song：信任等级重算 —— 每日 02:30 执行（cron 可配，默认 campus.trust-level.recalc-cron）
     * 重算最近活跃用户的 TL，TL3 不达标的自动降级。
     */
    @Scheduled(cron = "${campus.trust-level.recalc-cron:0 30 2 * * ?}")
    public void dailyTrustLevelRecalcTask() {
        try {
            int changed = trustLevelService.batchRecalculateAllActiveUsers();
            log.info("信任等级日重算完成, 变更 {} 人", changed);
        } catch (Exception e) {
            log.error("信任等级日重算失败", e);
        }
    }
}

