package com.campus.trend.campus_pulse.scheduled;

import com.campus.trend.campus_pulse.service.TrendScheduledTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class TrendTaskScheduler {

    private final TrendScheduledTaskService trendScheduledTaskService;

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

}
