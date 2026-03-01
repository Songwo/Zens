package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.config.properties.TrendScheduleProperties;
import com.campus.trend.campus_pulse.service.LevelService;
import com.campus.trend.campus_pulse.service.TrendScheduledTaskService;
import com.campus.trend.campus_pulse.service.ViewLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class TrendScheduledTaskServiceImpl implements TrendScheduledTaskService {

    private final LevelService levelService;
    private final ViewLogService viewLogService;
    private final TrendScheduleProperties trendScheduleProperties;

    @Override
    public void runDailyLevelUpgrade() {
        long start = System.currentTimeMillis();
        log.info("开始执行每日等级升级任务");
        try {
            levelService.batchUpgradeAllUsers();
            log.info("每日等级升级任务完成, costMs={}", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("每日等级升级任务失败", e);
        }
    }

    @Override
    public void runViewLogCleanup() {
        int retentionDays = Math.max(trendScheduleProperties.getViewLogRetentionDays(), 1);
        long start = System.currentTimeMillis();
        log.info("开始执行浏览日志清理任务, retentionDays={}", retentionDays);
        try {
            long removed = viewLogService.cleanOldLogs(retentionDays);
            log.info("浏览日志清理任务完成, removed={}, costMs={}",
                    removed, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("浏览日志清理任务失败", e);
        }
    }
}
